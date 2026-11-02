package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp.Kit;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.AttachEntityPacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cosmetics in the whole lobby: the menu, hats and gadgets in the hotbar, PvP, and who gets the particles. */
@EnvTest
class CosmeticsEnvTest {

    /** Longer than the menu's click cooldown, so each test click counts. */
    private static final long BETWEEN_CLICKS_MILLIS = 300;
    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir
    Path dir;

    private final List<LobbyServer> started = new ArrayList<>();
    private ConfigManager config;

    @AfterEach
    void stop() {
        started.forEach(LobbyServerTestAccess::stop);
    }

    private record Joined(Player player, TestConnection connection) {
    }

    private LobbyServer start(String cosmeticsYml) throws Exception {
        if (cosmeticsYml != null) {
            Files.writeString(dir.resolve(ConfigManager.COSMETICS_FILE), cosmeticsYml);
        }
        config = new ConfigManager(dir);
        config.load();
        LobbyServer lobby = LobbyServerTestAccess.start(config);
        started.add(lobby);
        return lobby;
    }

    private Joined join(Env env, LobbyServer lobby, String name, int protocol, Vec offset) {
        UUID id = UUID.randomUUID();
        if (protocol != 0) {
            BridgeTestAccess.reportVersion(lobby.bridge(), id, protocol);
        }
        TestConnection connection = env.createConnection(new GameProfile(id, name));
        Pos spawn = SpawnListener.spawnPosition(config.current().config());
        Player player = connection.connect(lobby.world().instance(), spawn);
        // Everyone joins at the spawn; then they walk to their place.
        if (!offset.isZero()) {
            player.teleport(spawn.add(offset)).join();
        }
        lobby.chat().settings().whenLoaded(id).join();
        return new Joined(player, connection);
    }

    /** Ticks, each time waiting for the effects thread to draw that tick's particles. */
    private static void ticks(Env env, LobbyServer lobby, int count) {
        for (int i = 0; i < count; i++) {
            env.tick();
            lobby.cosmetics().afterEffects().join();
        }
    }

    private static AbstractInventory awaitMenu(Env env, Player player, AbstractInventory before) {
        env.tickWhile(() -> player.getOpenInventory() == null || player.getOpenInventory() == before, WAIT);
        assertNotNull(player.getOpenInventory(), "a menu opened");
        return player.getOpenInventory();
    }

    private static void click(Player player, AbstractInventory inventory, Click click) throws InterruptedException {
        Thread.sleep(BETWEEN_CLICKS_MILLIS);
        EventDispatcher.call(new InventoryPreClickEvent(inventory, player, click));
    }

    @Test
    void theMenuListsWhatTheClientCanShowAndAClickPutsItOnAndSavesIt(Env env) throws Exception {
        LobbyServer lobby = start(null);
        Player steve = join(env, lobby, "Steve", 0, Vec.ZERO).player();
        Player old = join(env, lobby, "Old", ProtocolVersions.V1_8, new Vec(2, 0, 0)).player();

        lobby.menus().open(steve, CosmeticsMenu.NAME);
        AbstractInventory main = awaitMenu(env, steve, null);
        click(steve, main, new Click.Left(10));
        AbstractInventory trails = awaitMenu(env, steve, main);
        assertEquals(Material.BLAZE_POWDER, trails.getItemStack(0).material(), "flame, first in the file");
        assertEquals(Material.END_ROD, trails.getItemStack(4).material(), "sparkle needs 1.9: shown on the latest");

        // "notes" needs a permission Steve does not have.
        click(steve, trails, new Click.Left(2));
        assertNull(lobby.cosmetics().choices(steve).get(CosmeticType.TRAIL));
        click(steve, trails, new Click.Left(0));
        assertEquals("flame", lobby.cosmetics().equipped(steve, CosmeticType.TRAIL).id());
        assertEquals("trail=flame", lobby.chat().settings().get(steve.getUuid()).cosmetics(), "saved");
        click(steve, trails, new Click.Left(0));
        assertNull(lobby.cosmetics().equipped(steve, CosmeticType.TRAIL), "a second click takes it off");

        lobby.menus().open(old, CosmeticsMenu.NAME);
        AbstractInventory oldMain = awaitMenu(env, old, null);
        click(old, oldMain, new Click.Left(10));
        AbstractInventory oldTrails = awaitMenu(env, old, oldMain);
        assertEquals(Material.SUGAR, oldTrails.getItemStack(3).material());
        assertEquals(Material.FEATHER, oldTrails.getItemStack(4).material(),
                "1.8 cannot show sparkle, so it is not listed and the angel wings come next");
    }

    @Test
    void aPetAndABalloonFollowTheirOwnerForPlayersNearby(Env env) throws Exception {
        LobbyServer lobby = start(null);
        CosmeticsService cosmetics = lobby.cosmetics();
        Joined steve = join(env, lobby, "Steve", 0, Vec.ZERO);
        Joined near = join(env, lobby, "Near", 0, new Vec(3, 0, 0));
        Joined far = join(env, lobby, "Far", 0, new Vec(40, 0, 0));
        Collector<SpawnEntityPacket> nearSpawns = near.connection().trackIncoming(SpawnEntityPacket.class);
        Collector<SpawnEntityPacket> farSpawns = far.connection().trackIncoming(SpawnEntityPacket.class);
        Collector<AttachEntityPacket> leashes = near.connection().trackIncoming(AttachEntityPacket.class);

        assertEquals(CosmeticsService.Change.DONE, cosmetics.equip(steve.player(), cosmetics.config().find(CosmeticType.PET, "dog")));
        assertEquals(CosmeticsService.Change.DONE, cosmetics.equip(steve.player(), cosmetics.config().find(CosmeticType.BALLOON, "red")));
        assertEquals(CosmeticsService.Change.LOCKED, cosmetics.equip(steve.player(), cosmetics.config().find(CosmeticType.PET, "piggy")));
        ticks(env, lobby, 3);
        assertEquals(2, cosmetics.followerCount());
        List<SpawnEntityPacket> spawned = nearSpawns.collect();
        assertEquals(1, spawned.stream().filter(p -> p.type() == EntityType.WOLF).count(), "the pet");
        assertEquals(1, spawned.stream().filter(p -> p.type() == EntityType.ARMOR_STAND).count(), "the balloon");
        assertEquals(0, farSpawns.collect().size(), "40 blocks away: nothing");
        List<AttachEntityPacket> attached = leashes.collect();
        assertEquals(1, attached.size());
        assertEquals(steve.player().getEntityId(), attached.getFirst().holdingEntityId(), "the lead is in Steve's hand");

        int pet = cosmetics.followerIds(steve.player(), CosmeticType.PET).getFirst();
        Collector<EntityTeleportPacket> moves = near.connection().trackIncoming(EntityTeleportPacket.class);
        steve.player().teleport(steve.player().getPosition().add(4, 0, 0)).join();
        ticks(env, lobby, 6);
        assertTrue(moves.collect().stream().anyMatch(p -> p.entityId() == pet), "the pet walks after Steve");

        Collector<DestroyEntitiesPacket> removed = near.connection().trackIncoming(DestroyEntitiesPacket.class);
        cosmetics.unequip(steve.player(), CosmeticType.PET);
        ticks(env, lobby, 2);
        assertTrue(removed.collect().stream().anyMatch(p -> p.entityIds().contains(pet)), "taken off: it goes");
        assertEquals(1, cosmetics.followerCount());

        int balloon = cosmetics.followerIds(steve.player(), CosmeticType.BALLOON).getFirst();
        cosmetics.toggleHideOthers(near.player());
        Collector<DestroyEntitiesPacket> hidden = near.connection().trackIncoming(DestroyEntitiesPacket.class);
        ticks(env, lobby, Followers.VIEWERS_EVERY + 1);
        assertTrue(hidden.collect().stream().anyMatch(p -> p.entityIds().contains(balloon)),
                "the balloon goes for a player hiding other players' cosmetics");
    }

    @Test
    void anOutfitIsWornAndItsHelmetOnlyWithoutAHat(Env env) throws Exception {
        LobbyServer lobby = start("""
                outfits:
                  suit:
                    helmet: leather_helmet
                    chest: leather_chestplate
                    feet: iron_boots
                    color: "#ff0000"
                    permission: ""
                """);
        CosmeticsService cosmetics = lobby.cosmetics();
        Player steve = join(env, lobby, "Steve", 0, Vec.ZERO).player();

        cosmetics.equip(steve, cosmetics.config().find(CosmeticType.OUTFIT, "suit"));
        env.tickWhile(() -> steve.getEquipment(EquipmentSlot.CHESTPLATE).material() != Material.LEATHER_CHESTPLATE, WAIT);
        ItemStack chest = steve.getEquipment(EquipmentSlot.CHESTPLATE);
        assertEquals(HotbarService.OUTFIT_ITEM, chest.getTag(HotbarService.ITEM_TAG), "locked like the hotbar items");
        assertEquals(255, chest.get(DataComponents.DYED_COLOR).red(), "dyed");
        assertEquals(Material.IRON_BOOTS, steve.getEquipment(EquipmentSlot.BOOTS).material());
        assertTrue(steve.getEquipment(EquipmentSlot.LEGGINGS).isAir(), "no leggings in this outfit");
        assertEquals(Material.LEATHER_HELMET, steve.getEquipment(EquipmentSlot.HELMET).material(), "no hat: its helmet");

        cosmetics.equip(steve, cosmetics.config().find(CosmeticType.HAT, "glass"));
        env.tickWhile(() -> steve.getEquipment(EquipmentSlot.HELMET).material() != Material.GLASS, WAIT);
        assertEquals(Material.LEATHER_CHESTPLATE, steve.getEquipment(EquipmentSlot.CHESTPLATE).material(),
                "the hat wins the head, the rest stays");

        cosmetics.unequip(steve, CosmeticType.OUTFIT);
        env.tickWhile(() -> !steve.getEquipment(EquipmentSlot.CHESTPLATE).isAir(), WAIT);
        assertTrue(steve.getEquipment(EquipmentSlot.BOOTS).isAir());
        assertEquals(Material.GLASS, steve.getEquipment(EquipmentSlot.HELMET).material());
    }

    @Test
    void aPaintballPaintsForEveryoneNearbyAndThePaintGoesAway(Env env) throws Exception {
        LobbyServer lobby = start("""
                gadgets:
                  paint:
                    type: paintball
                    duration: 1
                    permission: ""
                  boom:
                    type: tnt
                    permission: ""
                """);
        CosmeticsService cosmetics = lobby.cosmetics();
        Joined steve = join(env, lobby, "Steve", 0, Vec.ZERO);
        Joined near = join(env, lobby, "Near", 0, new Vec(2, 0, 0));
        Player player = steve.player();
        cosmetics.equip(player, cosmetics.config().find(CosmeticType.GADGET, "paint"));

        // Looking at the sky: nothing to paint, and no cooldown.
        player.teleport(player.getPosition().withView(0, -90)).join();
        Collector<BlockChangePacket> missed = near.connection().trackIncoming(BlockChangePacket.class);
        cosmetics.useGadget(player);
        ticks(env, lobby, 2);
        assertEquals(0, missed.collect().size());

        // Looking down at the floor.
        player.teleport(player.getPosition().withView(0, 90)).join();
        Collector<BlockChangePacket> painted = near.connection().trackIncoming(BlockChangePacket.class);
        cosmetics.useGadget(player);
        ticks(env, lobby, 2);
        List<BlockChangePacket> paint = painted.collect();
        assertTrue(paint.size() > 0, "the floor is painted");
        Block floor = player.getInstance().getBlock(paint.getFirst().blockPosition());
        assertTrue(paint.stream().noneMatch(p -> p.blockStateId() == floor.stateId()), "in paint colors");
        assertEquals(floor, player.getInstance().getBlock(paint.getFirst().blockPosition()), "the map does not change");

        Collector<BlockChangePacket> restored = near.connection().trackIncoming(BlockChangePacket.class);
        ticks(env, lobby, 25);
        List<BlockChangePacket> back = restored.collect();
        assertEquals(paint.size(), back.size(), "after the duration every block is put back");
        assertTrue(back.stream().allMatch(p -> p.blockStateId()
                == player.getInstance().getBlock(p.blockPosition()).stateId()));

        // A TNT blinks, explodes, and pushes the player next to it.
        cosmetics.equip(player, cosmetics.config().find(CosmeticType.GADGET, "boom"));
        Collector<SpawnEntityPacket> tnt = near.connection().trackIncoming(SpawnEntityPacket.class);
        cosmetics.useGadget(player);
        ticks(env, lobby, 2);
        assertEquals(1, tnt.collect().stream().filter(p -> p.type() == EntityType.TNT).count());
        near.player().setVelocity(Vec.ZERO);
        ticks(env, lobby, CosmeticsService.TNT_FUSE_TICKS);
        assertTrue(near.player().getVelocity().y() > 0, "pushed away and up");
    }

    @Test
    void theHatGoesOnTheHeadAndTheGadgetInTheHotbarWithACooldown(Env env) throws Exception {
        LobbyServer lobby = start(null);
        CosmeticsService cosmetics = lobby.cosmetics();
        Player steve = join(env, lobby, "Steve", 0, Vec.ZERO).player();

        assertEquals(CosmeticsService.Change.DONE, cosmetics.equip(steve, cosmetics.config().find(CosmeticType.HAT, "glass")));
        env.tickWhile(() -> steve.getEquipment(EquipmentSlot.HELMET).material() != Material.GLASS, WAIT);
        assertEquals(HotbarService.HAT_ITEM, steve.getEquipment(EquipmentSlot.HELMET).getTag(HotbarService.ITEM_TAG),
                "locked like the hotbar items");
        assertEquals(CosmeticsService.Change.LOCKED,
                cosmetics.equip(steve, cosmetics.config().find(CosmeticType.HAT, "melon")));

        cosmetics.equip(steve, cosmetics.config().find(CosmeticType.GADGET, "leap"));
        env.tickWhile(() -> steve.getInventory().getItemStack(4).material() != Material.FEATHER, WAIT);
        steve.setVelocity(Vec.ZERO);
        cosmetics.useGadget(steve);
        assertTrue(steve.getVelocity().y() > 0, "the leap throws the player up");
        steve.setVelocity(Vec.ZERO);
        cosmetics.useGadget(steve);
        assertEquals(Vec.ZERO, steve.getVelocity(), "on cooldown");

        cosmetics.unequip(steve, CosmeticType.HAT);
        env.tickWhile(() -> !steve.getEquipment(EquipmentSlot.HELMET).isAir(), WAIT);
        assertTrue(steve.getEquipment(EquipmentSlot.HELMET).isAir());
    }

    @Test
    void cosmeticsAreOffInThePvpArea(Env env) throws Exception {
        LobbyServer lobby = start(null);
        CosmeticsService cosmetics = lobby.cosmetics();
        lobby.pvp().setRegion(new Region("wand", "selection", -10, 30, -10, 10, 90, 10));
        lobby.pvp().setKit(new Kit(Map.of(0, ItemStack.of(Material.IRON_SWORD)),
                Map.of(EquipmentSlot.HELMET, ItemStack.of(Material.IRON_HELMET))));
        Player steve = join(env, lobby, "Steve", 0, Vec.ZERO).player();
        env.tick();
        env.tick();
        assertTrue(lobby.pvp().inArea(steve));
        assertTrue(cosmetics.suppressed(steve));

        cosmetics.equip(steve, cosmetics.config().find(CosmeticType.HAT, "glass"));
        cosmetics.equip(steve, cosmetics.config().find(CosmeticType.GADGET, "leap"));
        ticks(env, lobby, 5);
        assertEquals(Material.IRON_HELMET, steve.getEquipment(EquipmentSlot.HELMET).material(), "the kit stays");
        steve.setVelocity(Vec.ZERO);
        cosmetics.useGadget(steve);
        assertEquals(Vec.ZERO, steve.getVelocity(), "no gadgets in the PvP area");

        lobby.pvp().delete();
        env.tickWhile(() -> steve.getEquipment(EquipmentSlot.HELMET).material() != Material.GLASS, WAIT);
        assertFalse(cosmetics.suppressed(steve));
    }

    @Test
    void trailParticlesOnlyReachPlayersNearbyWhoWantThem(Env env) throws Exception {
        LobbyServer lobby = start(null);
        CosmeticsService cosmetics = lobby.cosmetics();
        Joined steve = join(env, lobby, "Steve", 0, Vec.ZERO);
        Joined near = join(env, lobby, "Near", 0, new Vec(3, 0, 0));
        Joined far = join(env, lobby, "Far", 0, new Vec(40, 0, 0));
        // A halo draws while standing still too.
        cosmetics.equip(steve.player(), cosmetics.config().find(CosmeticType.TRAIL, "hearts"));

        Collector<ParticlePacket> own = steve.connection().trackIncoming(ParticlePacket.class);
        Collector<ParticlePacket> nearby = near.connection().trackIncoming(ParticlePacket.class);
        Collector<ParticlePacket> distant = far.connection().trackIncoming(ParticlePacket.class);
        ticks(env, lobby, 20);
        assertTrue(own.collect().size() > 0, "the player sees their own trail");
        assertTrue(nearby.collect().size() > 0, "a player 3 blocks away sees it");
        assertEquals(0, distant.collect().size(), "a player 40 blocks away does not");

        cosmetics.toggleHideOthers(near.player());
        Collector<ParticlePacket> hidden = near.connection().trackIncoming(ParticlePacket.class);
        ticks(env, lobby, 20);
        assertEquals(0, hidden.collect().size(), "hide other players' cosmetics");
    }

    @Test
    void theLimitsCapWhatOnePlayerReceives(Env env) throws Exception {
        LobbyServer lobby = start("limits:\n  per-viewer: 2\n  per-tick: 1500\n");
        CosmeticsService cosmetics = lobby.cosmetics();
        Joined steve = join(env, lobby, "Steve", 0, Vec.ZERO);
        // A halo is 6 particles at once; only 2 may reach a player in one tick.
        cosmetics.equip(steve.player(), cosmetics.config().find(CosmeticType.TRAIL, "hearts"));
        Collector<ParticlePacket> own = steve.connection().trackIncoming(ParticlePacket.class);
        ticks(env, lobby, 20);
        int received = own.collect().size();
        assertTrue(received > 0 && received <= 20 / CosmeticEffects.HALO_EVERY * 2 + 2, received + " packets");
        assertTrue(cosmetics.droppedParticles() > 0);
    }

    @Test
    void aRightClickPreviewsOnlyForThatPlayerWithoutPuttingItOn(Env env) throws Exception {
        LobbyServer lobby = start(null);
        Joined steve = join(env, lobby, "Steve", 0, Vec.ZERO);
        Joined near = join(env, lobby, "Near", 0, new Vec(3, 0, 0));
        Player player = steve.player();
        lobby.menus().open(player, CosmeticsMenu.NAME);
        AbstractInventory main = awaitMenu(env, player, null);
        click(player, main, new Click.Left(10));
        AbstractInventory trails = awaitMenu(env, player, main);

        Collector<ParticlePacket> own = steve.connection().trackIncoming(ParticlePacket.class);
        Collector<ParticlePacket> nearby = near.connection().trackIncoming(ParticlePacket.class);
        // "notes" is locked, but a preview is allowed.
        click(player, trails, new Click.Right(2));
        ticks(env, lobby, 20);
        assertNull(player.getOpenInventory(), "the menu closes to watch the preview");
        assertTrue(own.collect().size() > 0);
        assertEquals(0, nearby.collect().size(), "only the player sees a preview");
        assertNull(lobby.cosmetics().choices(player).get(CosmeticType.TRAIL));
    }
}
