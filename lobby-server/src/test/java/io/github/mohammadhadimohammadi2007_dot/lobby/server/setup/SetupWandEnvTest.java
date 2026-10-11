package io.github.mohammadhadimohammadi2007_dot.lobby.server.setup;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The setup wand in the real lobby: corners, selecting objects, the outline. */
@EnvTest
class SetupWandEnvTest {

    @TempDir
    Path dir;

    @Test
    void hittingAndRightClickingBlocksSetTheCorners(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        LobbyServer lobby = LobbyServerTestAccess.start(config);
        try {
            SetupWand wand = lobby.wand();
            var connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Admin"));
            Player admin = connection.connect(lobby.world().instance(), new Pos(0.5, 65, 0.5));
            admin.getInventory().setItemStack(admin.getHeldSlot(), wand.item(admin));
            var chat = connection.trackIncoming(SystemChatPacket.class);

            var first = new PlayerStartDiggingEvent(admin, admin.getInstance(), Block.STONE, new BlockVec(1, 64, 1), BlockFace.TOP);
            env.process().eventHandler().call(first);
            var second = new PlayerBlockInteractEvent(admin, PlayerHand.MAIN, admin.getInstance(), Block.STONE,
                    new BlockVec(3, 66, 2), Vec.ZERO, BlockFace.TOP);
            env.process().eventHandler().call(second);

            assertTrue(first.isCancelled(), "the wand does not break blocks");
            assertTrue(second.isCancelled());
            Region region = wand.region(admin, "portal", "test").orElseThrow();
            assertEquals(new Region("portal", "test", 1, 64, 1, 3, 66, 2), region);
            List<String> lines = chat.collect().stream()
                    .map(packet -> PlainTextComponentSerializer.plainText().serialize(packet.message())).toList();
            assertTrue(lines.getLast().contains("Selection: 18 blocks"), lines.toString());

            // A portal is made from that selection (Admin has no rights here, so the console makes it: it needs
            // Admin's selection, so this checks the selection is the one portals use).
            assertEquals(io.github.mohammadhadimohammadi2007_dot.lobby.server.portal.PortalService.CreateResult.CREATED,
                    lobby.portals().create(admin, "test"));
        } finally {
            LobbyServerTestAccess.stop(lobby);
        }
    }

    @Test
    void clickingAHologramWithTheWandSelectsItInsteadOfRunningItsActions(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        LobbyServer lobby = LobbyServerTestAccess.start(config);
        try {
            var commands = env.process().command();
            var console = new ConsoleSender();
            commands.execute(console, "hologram create shop 2 66 2");
            commands.execute(console, "hologram action add shop teleport: 100 70 100");
            var connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Admin"));
            // Far away first, so the hologram appears only after the packets are tracked.
            Player admin = connection.connect(lobby.world().instance(), new Pos(0.5, 65, 200));
            // collect() stops a collector, so a new one starts before the old one is read.
            List<net.minestom.testing.Collector<SpawnEntityPacket>> spawns =
                    new ArrayList<>(List.of(connection.trackIncoming(SpawnEntityPacket.class)));
            admin.teleport(new Pos(0.5, 65, 0.5)).join();
            // Refresh by hand instead of waiting for the renderer's timer, which is slow on a busy machine.
            List<SpawnEntityPacket> seen = new ArrayList<>();
            for (int refresh = 0; refresh < 5 && seen.stream().noneMatch(p -> p.type().equals(EntityType.TEXT_DISPLAY));
                 refresh++) {
                lobby.renderer().refreshNow();
                spawns.add(connection.trackIncoming(SpawnEntityPacket.class));
                seen.addAll(spawns.removeFirst().collect());
            }
            spawns.forEach(net.minestom.testing.Collector::collect);
            int hologramId = seen.stream().filter(p -> p.type().equals(EntityType.TEXT_DISPLAY)).findFirst()
                    .orElseThrow().entityId();
            admin.getInventory().setItemStack(admin.getHeldSlot(), lobby.wand().item(admin));

            env.process().eventHandler().call(new PlayerPacketEvent(admin, new ClientAttackPacket(hologramId)));
            env.tickWhile(() -> false, Duration.ofMillis(200));

            SetupWand.Selected selected = lobby.wand().selected(admin).orElseThrow();
            assertEquals(SetupWand.Kind.HOLOGRAM, selected.kind());
            assertEquals("shop", selected.name());
            assertTrue(selected.at().samePoint(new Vec(2, 66, 2)), selected.at().toString());
            assertTrue(admin.getPosition().x() < 50, "the teleport action did not run");
        } finally {
            LobbyServerTestAccess.stop(lobby);
        }
    }

    @Test
    void theOutlineStaysSmallForBigSelections() {
        assertEquals(SetupWand.MAX_OUTLINE_POINTS, SetupWand.outline(new Vec(0, 0, 0), new Vec(200, 100, 200)).size());
        List<Vec> single = SetupWand.outline(new Vec(5, 5, 5), new Vec(5, 5, 5));
        assertFalse(single.isEmpty());
        assertTrue(single.stream().allMatch(p -> p.x() >= 5 && p.x() <= 6 && p.y() >= 5 && p.y() <= 6), "one block");
    }
}
