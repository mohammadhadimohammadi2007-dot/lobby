package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operators by UUID, the offline-mode protection, and moving the old list from config.yml. */
@EnvTest
class OpListEnvTest {

    @TempDir
    Path dir;

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Map<String, String> addressOf = new HashMap<>();

    private OpList ops(OpList.Protection protection, boolean namesTrusted) {
        OpList.Settings settings = new OpList.Settings(protection, namesTrusted);
        return new OpList(dir, () -> settings, now::get, player -> addressOf.getOrDefault(player.getUsername(), "10.0.0.1"));
    }

    private static Player join(Env env, Instance instance, String name, UUID uuid) {
        return env.createConnection(new GameProfile(uuid, name)).connect(instance, new Pos(0, 42, 0));
    }

    private static Player offline(Env env, Instance instance, String name) {
        return join(env, instance, name, OpList.offlineUuid(name));
    }

    @Test
    void offlineUuidsAreTheVanillaOnes() {
        // What Bukkit and vanilla give "Notch" in offline mode.
        assertEquals(UUID.fromString("b50ad385-829d-3141-a216-7e7d7539ba7f"), OpList.offlineUuid("Notch"));
    }

    @Test
    void aNameAloneIsNotEnoughInOfflineMode(Env env) {
        Instance instance = env.createFlatInstance();
        OpList ops = ops(OpList.Protection.CODE_AND_IP, false);
        ops.op("Steve", OpList.offlineUuid("Steve"));

        Player steve = offline(env, instance, "Steve");
        Optional<String> code = ops.joined(steve);

        assertTrue(code.isPresent(), "a code is printed in the console");
        assertFalse(ops.isOp(steve), "not an operator before confirming");
        assertEquals(OpList.ConfirmResult.WRONG_CODE, ops.confirm(steve, "000000".equals(code.get()) ? "111111" : "000000"));
        assertFalse(ops.isOp(steve));
        assertEquals(OpList.ConfirmResult.CONFIRMED, ops.confirm(steve, code.get()));
        assertTrue(ops.isOp(steve));
    }

    @Test
    void aConfirmedAddressWorksAndANewOneNeedsANewCode(Env env) {
        Instance instance = env.createFlatInstance();
        OpList ops = ops(OpList.Protection.CODE_AND_IP, false);
        ops.op("Steve", OpList.offlineUuid("Steve"));
        Player steve = offline(env, instance, "Steve");
        ops.confirm(steve, ops.joined(steve).orElseThrow());
        ops.left(steve.getUuid());

        // Same address again: right away.
        assertTrue(ops.joined(steve).isEmpty());
        assertTrue(ops.isOp(steve));
        ops.left(steve.getUuid());

        // Someone with the same name from elsewhere: no rights, a new code.
        addressOf.put("Steve", "198.51.100.9");
        assertTrue(ops.joined(steve).isPresent());
        assertFalse(ops.isOp(steve));
    }

    @Test
    void withCodeTheAddressDoesNotMatterAfterTheFirstTime(Env env) {
        Instance instance = env.createFlatInstance();
        OpList ops = ops(OpList.Protection.CODE, false);
        ops.op("Steve", OpList.offlineUuid("Steve"));
        Player steve = offline(env, instance, "Steve");
        ops.confirm(steve, ops.joined(steve).orElseThrow());
        ops.left(steve.getUuid());

        addressOf.put("Steve", "198.51.100.9");
        assertTrue(ops.joined(steve).isEmpty());
        assertTrue(ops.isOp(steve));
    }

    @Test
    void codesExpireAndStopAfterFiveWrongTries(Env env) {
        Instance instance = env.createFlatInstance();
        OpList ops = ops(OpList.Protection.CODE_AND_IP, false);
        ops.op("Steve", OpList.offlineUuid("Steve"));
        Player steve = offline(env, instance, "Steve");

        String code = ops.joined(steve).orElseThrow();
        now.addAndGet(OpList.CODE_VALID_MILLIS + 1);
        assertEquals(OpList.ConfirmResult.NO_CODE, ops.confirm(steve, code), "too late");

        String second = ops.joined(steve).orElseThrow();
        String wrong = second.equals("123456") ? "654321" : "123456";
        for (int i = 0; i < OpList.MAX_CODE_ATTEMPTS; i++) {
            assertEquals(OpList.ConfirmResult.WRONG_CODE, ops.confirm(steve, wrong));
        }
        assertEquals(OpList.ConfirmResult.NO_CODE, ops.confirm(steve, second), "no guessing a million codes");
        assertFalse(ops.isOp(steve));
    }

    @Test
    void whereNamesAreCheckedAnOpIsAnOpRightAway(Env env) {
        Instance instance = env.createFlatInstance();
        OpList ops = ops(OpList.Protection.CODE_AND_IP, true);
        UUID mojang = UUID.randomUUID();
        ops.op("Notch", mojang);

        Player notch = join(env, instance, "Notch", mojang);
        assertTrue(ops.joined(notch).isEmpty());
        assertTrue(ops.isOp(notch));

        Player impostor = join(env, instance, "notch", UUID.randomUUID());
        ops.joined(impostor);
        assertFalse(ops.isOp(impostor), "another account with the same name is not the operator");
    }

    @Test
    void behindAProxyTheNameIsBoundToTheUuidOnTheFirstJoin(Env env) throws Exception {
        Instance instance = env.createFlatInstance();
        OpList ops = ops(OpList.Protection.CODE_AND_IP, true);
        ops.op("Alex", null);
        assertEquals("pending-alex", ops.all().getFirst().key());

        UUID forwarded = UUID.randomUUID();
        Player alex = join(env, instance, "Alex", forwarded);
        ops.joined(alex);

        assertTrue(ops.isOp(alex));
        assertEquals(forwarded, ops.all().getFirst().uuid());
        ops.close();
        assertTrue(Files.readString(dir.resolve("data").resolve("ops.yml")).contains(forwarded.toString()),
                "ops.yml now has the UUID");
    }

    @Test
    void anOnlinePlayerOppedByStaffIsConfirmedFromTheirAddress(Env env) {
        Instance instance = env.createFlatInstance();
        OpList ops = ops(OpList.Protection.CODE_AND_IP, false);
        Player steve = offline(env, instance, "Steve");
        ops.joined(steve);

        ops.op(steve);

        assertTrue(ops.isOp(steve));
        assertEquals(List.of("10.0.0.1"), List.copyOf(ops.all().getFirst().ips()));
        assertEquals("Steve", ops.deop("steve").orElseThrow().name());
        assertFalse(ops.isOp(steve));
        assertTrue(ops.all().isEmpty());
    }

    @Test
    void protectionOffTrustsTheName(Env env) {
        Instance instance = env.createFlatInstance();
        OpList ops = ops(OpList.Protection.OFF, false);
        ops.op("Steve", OpList.offlineUuid("Steve"));
        Player steve = offline(env, instance, "Steve");

        assertTrue(ops.joined(steve).isEmpty());
        assertTrue(ops.isOp(steve));
    }

    @Test
    void theOldOperatorsListIsMovedOnce(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Path file = dir.resolve(ConfigManager.CONFIG_FILE);
        Files.writeString(file, Files.readString(file) + """

                # ---------------- Operators (old) ----------------
                # Only used when LuckPerms is disabled (see integrations.yml).
                # Usernames listed here get every permission. Example: ["Notch", "jeb_"]
                operators:
                  - Notch
                  - jeb_
                """);
        config.load();
        OpList ops = ops(OpList.Protection.CODE_AND_IP, false);

        // Standalone offline mode (the bundled default): offline UUIDs, a code on the first join.
        assertEquals(2, OpMigration.migrate(file, config.current().config(), ops, new MojangProfiles()));

        assertEquals(List.of("Notch", "jeb_"), ops.all().stream().map(Op::name).toList());
        assertEquals(OpList.offlineUuid("Notch"), ops.all().getFirst().uuid());
        assertFalse(ops.all().getFirst().confirmed());
        String text = Files.readString(file);
        assertFalse(text.contains("- Notch"), text);
        assertTrue(text.contains("# operators: moved to data/ops.yml"), text);
        assertFalse(text.contains("Usernames listed here"));
        config.load();
        assertTrue(config.current().config().legacyOperators().isEmpty());
        assertEquals(0, OpMigration.migrate(file, config.current().config(), ops, new MojangProfiles()), "only once");
    }

    @Test
    void aBrokenEntryInOpsYmlIsSkipped(Env env) throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(dir.resolve("data").resolve("ops.yml"), """
                not-a-uuid:
                  name: Steve
                069a79f4-44e9-4726-a5be-fca90e38aaf5:
                  name: Notch
                  confirmed: true
                """);
        OpList ops = ops(OpList.Protection.OFF, true);
        assertEquals(List.of("Notch"), ops.all().stream().map(Op::name).toList());
        assertNull(ops.all().stream().filter(op -> op.name().equals("Steve")).findFirst().orElse(null));
    }
}
