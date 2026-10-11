package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.AtomicFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/**
 * Turns names into the UUIDs this server will see, and moves the old {@code operators:} list of
 * config.yml into data/ops.yml once.
 */
public final class OpMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpMigration.class);
    private static final Pattern OPERATORS_KEY = Pattern.compile("^operators:\\s*(.*)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s+-.*$");

    private OpMigration() {
    }

    /**
     * The UUID this server will see for {@code name}: the offline UUID in offline mode, Mojang's in online
     * mode, unknown behind a proxy (bound when they first join). Blocking (asks Mojang).
     *
     * @return empty if no premium account has this name (online mode); an empty inner value for "not known yet"
     * @throws CompletionException with an {@link IOException} if Mojang could not be asked
     */
    public static Optional<Optional<UUID>> resolve(LobbyConfig config, String name, MojangProfiles mojang) {
        if (config.connection().mode() != ConnectionMode.STANDALONE) {
            return Optional.of(Optional.empty());
        }
        if (!config.connection().onlineMode()) {
            return Optional.of(Optional.of(OpList.offlineUuid(name)));
        }
        try {
            return mojang.uuidOf(name).map(Optional::of);
        } catch (IOException e) {
            throw new CompletionException(new IOException("could not ask Mojang (" + e.getMessage() + ")", e));
        }
    }

    /**
     * Moves the names of the old {@code operators:} list to data/ops.yml and removes the list from
     * config.yml, leaving a comment. Does nothing if there is no such list.
     *
     * @return how many operators were moved
     */
    public static int migrate(Path configFile, LobbyConfig config, OpList ops, MojangProfiles mojang) {
        List<String> names = config.legacyOperators();
        if (names.isEmpty() && !hasOperatorsKey(configFile)) {
            return 0;
        }
        List<String> moved = new ArrayList<>();
        for (String name : names) {
            if (ops.contains(name)) {
                continue;
            }
            UUID uuid;
            try {
                Optional<Optional<UUID>> resolved = resolve(config, name, mojang);
                if (resolved.isEmpty()) {
                    LOGGER.warn("Not moved to ops.yml: there is no Minecraft account called '{}'.", name);
                    continue;
                }
                uuid = resolved.get().orElse(null);
            } catch (CompletionException e) {
                // Mojang could not be asked: the name is bound when they join (names are checked in online mode).
                uuid = null;
            }
            ops.op(name, uuid);
            moved.add(name);
        }
        try {
            removeOperatorsKey(configFile);
        } catch (IOException e) {
            LOGGER.warn("Could not remove the old operators list from config.yml: {}. It is ignored from now on.",
                    e.getMessage());
        }
        if (!moved.isEmpty()) {
            LOGGER.warn("Moved {} operator(s) from config.yml to data/ops.yml: {}. Manage them with /op and /deop from"
                    + " now on.", moved.size(), String.join(", ", moved));
            if (config.ops().protection() != OpList.Protection.OFF && !config.namesTrusted()) {
                LOGGER.warn("Names are not checked in offline mode, so each of them must confirm once with a code from"
                        + " this console (/op confirm <code>) the first time they join.");
            }
        }
        return moved.size();
    }

    private static boolean hasOperatorsKey(Path configFile) {
        try {
            return Files.readAllLines(configFile, StandardCharsets.UTF_8).stream()
                    .anyMatch(line -> OPERATORS_KEY.matcher(line).matches());
        } catch (IOException e) {
            return false;
        }
    }

    /** Replaces the {@code operators:} key (inline or as a list) with a comment; the rest stays as it was. */
    static void removeOperatorsKey(Path configFile) throws IOException {
        List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);
        List<String> result = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!OPERATORS_KEY.matcher(line).matches()) {
                result.add(line);
                continue;
            }
            changed = true;
            // The old comments right above it.
            while (!result.isEmpty() && (result.getLast().startsWith("# Only used when LuckPerms is disabled")
                    || result.getLast().startsWith("# Usernames listed here get every permission"))) {
                result.removeLast();
            }
            result.add("# operators: moved to data/ops.yml on " + LocalDate.now() + ". Use /op and /deop"
                    + " (docs/console-and-ops.md).");
            while (i + 1 < lines.size() && LIST_ITEM.matcher(lines.get(i + 1)).matches()) {
                i++;
            }
        }
        if (changed) {
            AtomicFiles.writeString(configFile, String.join(System.lineSeparator(), result) + System.lineSeparator());
        }
    }
}
