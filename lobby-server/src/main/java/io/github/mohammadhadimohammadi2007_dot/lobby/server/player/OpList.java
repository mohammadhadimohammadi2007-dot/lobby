package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.YamlDataStore;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The operators in {@code data/ops.yml}, stored by UUID with their last known name.
 *
 * <p>Where names cannot be trusted (standalone offline mode, and optionally behind a cracked proxy) a name
 * or offline UUID alone never makes someone an operator: with {@code code-and-ip} an operator only has their
 * rights from addresses they confirmed with a one-time code printed in the console; with {@code code} the
 * code is needed once. See docs/console-and-ops.md.
 *
 * <p>{@link #isOp(Player)} is a set lookup, so it is cheap enough for every permission check.
 */
public final class OpList implements Operators {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpList.class);
    /** How long a confirmation code stays valid. */
    static final long CODE_VALID_MILLIS = 10 * 60_000L;
    /** Wrong codes allowed before the code is dropped (a new one comes with the next join). */
    static final int MAX_CODE_ATTEMPTS = 5;
    private static final int CODE_DIGITS = 6;

    /** {@code ops.protection} in config.yml. */
    public enum Protection { CODE_AND_IP, CODE, OFF }

    /**
     * @param protection    how operators prove who they are where names cannot be trusted
     * @param namesTrusted  true behind an online-mode check (Mojang in online standalone, or a trusted proxy):
     *                      then {@code protection} does not apply
     */
    public record Settings(Protection protection, boolean namesTrusted) {

        boolean protects() {
            return protection != Protection.OFF && !namesTrusted;
        }
    }

    /** What {@link #confirm} did. */
    public enum ConfirmResult { CONFIRMED, WRONG_CODE, NO_CODE }

    /** A code waiting for {@code /op confirm}. */
    private record PendingCode(String code, long expiresAtMillis, int attemptsLeft) {
    }

    private final YamlDataStore<Op> store;
    private final Supplier<Settings> settings;
    private final LongSupplier clock;
    private final Function<Player, String> addresses;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Op> ops = new LinkedHashMap<>();
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingCode> codes = new ConcurrentHashMap<>();

    public OpList(Path dataDir, Supplier<Settings> settings) {
        this(dataDir, settings, System::currentTimeMillis, OpList::ip);
    }

    /** For tests: a clock and the players' addresses. */
    OpList(Path dataDir, Supplier<Settings> settings, LongSupplier clock, Function<Player, String> addresses) {
        this.store = new YamlDataStore<>(dataDir.resolve("data").resolve("ops.yml"),
                " Operators: every permission. Managed with /op and /deop (console or in game).\n"
                        + " Stored by UUID; \"pending-<name>\" entries get their UUID on the first join.\n"
                        + " Remove \"ips\" (or set confirmed: false) to make someone confirm again with a code.",
                new Op.Codec());
        this.settings = settings;
        this.clock = clock;
        this.addresses = addresses;
        synchronized (this) {
            store.load().values().forEach(op -> ops.put(op.key(), op));
        }
    }

    /** The UUID an offline-mode server gives {@code name}, as vanilla and Bukkit do. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isOp(Player player) {
        return active.contains(player.getUuid());
    }

    /** Every operator, for {@code /ops}. */
    public synchronized List<Op> all() {
        return List.copyOf(ops.values());
    }

    /** True if {@code player} was given a code to confirm. */
    public boolean hasPendingCode(UUID player) {
        PendingCode code = codes.get(player);
        return code != null && code.expiresAtMillis() > clock.getAsLong();
    }

    /**
     * Checks a player who just joined: binds a pending name to their UUID, and either gives them their rights
     * or a code to confirm.
     *
     * @return the code they must confirm with (also logged for the console), or empty
     */
    public synchronized Optional<String> joined(Player player) {
        UUID uuid = player.getUuid();
        Op op = ops.get(uuid.toString());
        if (op == null) {
            Op pending = ops.get("pending-" + player.getUsername().toLowerCase(Locale.ROOT));
            if (pending == null) {
                return Optional.empty();
            }
            ops.remove(pending.key());
            op = pending.withUuid(uuid);
            LOGGER.info("Operator {} joined for the first time; their UUID {} is now in ops.yml", player.getUsername(), uuid);
        }
        if (!op.name().equals(player.getUsername())) {
            op = op.withName(player.getUsername());
        }
        ops.put(op.key(), op);
        save();
        String ip = addresses.apply(player);
        if (allowed(op, ip)) {
            active.add(uuid);
            return Optional.empty();
        }
        active.remove(uuid);
        String code = newCode();
        codes.put(uuid, new PendingCode(code, clock.getAsLong() + CODE_VALID_MILLIS, MAX_CODE_ATTEMPTS));
        LOGGER.warn("{} is an operator, but joined from {}, which they have not confirmed, so they are NOT an operator"
                        + " for now. If this really is {}, tell them to type: /op confirm {} (valid 10 minutes)."
                        + " Anyone can join with any name in offline mode, so never give this code to someone you"
                        + " cannot verify.", player.getUsername(), ip == null ? "an unknown address" : ip,
                player.getUsername(), code);
        return Optional.of(code);
    }

    /** Forgets the player's rights for this connection. */
    public void left(UUID player) {
        active.remove(player);
        codes.remove(player);
    }

    /** {@code /op confirm <code>}. */
    public synchronized ConfirmResult confirm(Player player, String typed) {
        UUID uuid = player.getUuid();
        PendingCode pending = codes.get(uuid);
        if (pending == null || pending.expiresAtMillis() <= clock.getAsLong()) {
            codes.remove(uuid);
            return ConfirmResult.NO_CODE;
        }
        if (!pending.code().equals(typed.strip())) {
            if (pending.attemptsLeft() <= 1) {
                codes.remove(uuid);
                LOGGER.warn("{} typed a wrong operator code {} times; the code is no longer valid.", player.getUsername(),
                        MAX_CODE_ATTEMPTS);
            } else {
                codes.put(uuid, new PendingCode(pending.code(), pending.expiresAtMillis(), pending.attemptsLeft() - 1));
            }
            return ConfirmResult.WRONG_CODE;
        }
        codes.remove(uuid);
        Op op = ops.get(uuid.toString());
        if (op == null) {
            return ConfirmResult.NO_CODE;
        }
        String ip = addresses.apply(player);
        ops.put(op.key(), op.confirmedFrom(ip));
        save();
        active.add(uuid);
        LOGGER.info("{} confirmed their operator rights from {}", player.getUsername(), ip);
        return ConfirmResult.CONFIRMED;
    }

    /**
     * Makes an online player an operator. They were chosen by the console or another operator while
     * connected, so this connection counts as confirmed.
     */
    public synchronized void op(Player player) {
        Op op = ops.get(player.getUuid().toString());
        Op updated = (op != null ? op : new Op(player.getUuid(), player.getUsername(), false, Set.of()))
                .withName(player.getUsername()).confirmedFrom(addresses.apply(player));
        ops.remove("pending-" + player.getUsername().toLowerCase(Locale.ROOT));
        ops.put(updated.key(), updated);
        save();
        active.add(player.getUuid());
        codes.remove(player.getUuid());
    }

    /**
     * Makes a player who is not online an operator.
     *
     * @param uuid their UUID if known (Mojang in online mode, the offline UUID in offline mode), else
     *             {@code null}: the name is bound to a UUID when they first join
     */
    public synchronized void op(String name, @Nullable UUID uuid) {
        Op op = new Op(uuid, name, !settings.get().protects(), Set.of());
        if (uuid != null) {
            ops.remove("pending-" + name.toLowerCase(Locale.ROOT));
            Op existing = ops.get(uuid.toString());
            if (existing != null) {
                op = existing.withName(name);
            }
        }
        ops.put(op.key(), op);
        save();
    }

    /**
     * Takes the rights away from whoever has this name (or UUID, as text).
     *
     * @return the removed operator, or empty if nobody matched
     */
    public synchronized Optional<Op> deop(String nameOrUuid) {
        for (Op op : new ArrayList<>(ops.values())) {
            if (op.name().equalsIgnoreCase(nameOrUuid) || (op.uuid() != null && op.uuid().toString().equalsIgnoreCase(nameOrUuid))) {
                ops.remove(op.key());
                if (op.uuid() != null) {
                    active.remove(op.uuid());
                    codes.remove(op.uuid());
                }
                save();
                return Optional.of(op);
            }
        }
        return Optional.empty();
    }

    /** True if someone with this name is in the list (confirmed or not). */
    public synchronized boolean contains(String name) {
        return ops.values().stream().anyMatch(op -> op.name().equalsIgnoreCase(name));
    }

    /**
     * Reads the list again (after editing ops.yml by hand) and checks everyone online again.
     *
     * @param online the players online now
     */
    public synchronized void reload(Iterable<Player> online) {
        ops.clear();
        store.load().values().forEach(op -> ops.put(op.key(), op));
        active.clear();
        for (Player player : online) {
            Op op = ops.get(player.getUuid().toString());
            if (op != null && allowed(op, addresses.apply(player))) {
                active.add(player.getUuid());
            }
        }
    }

    /** Writes what is still waiting and stops the writer. Called on shutdown. */
    public void close() {
        store.close();
    }

    private boolean allowed(Op op, @Nullable String ip) {
        Settings current = settings.get();
        if (!current.protects()) {
            return true;
        }
        if (!op.confirmed()) {
            return false;
        }
        return current.protection() != Protection.CODE_AND_IP || (ip != null && op.ips().contains(ip));
    }

    private void save() {
        store.save(new LinkedHashMap<>(ops));
    }

    private String newCode() {
        int bound = (int) Math.pow(10, CODE_DIGITS);
        return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", random.nextInt(bound));
    }

    /** The player's address as text, or {@code null} if unknown (fake test connections). */
    static @Nullable String ip(Player player) {
        SocketAddress address = player.getPlayerConnection().getRemoteAddress();
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }
        return null;
    }
}
