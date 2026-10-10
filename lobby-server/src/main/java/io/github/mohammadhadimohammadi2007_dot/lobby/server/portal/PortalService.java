package io.github.mohammadhadimohammadi2007_dot.lobby.server.portal;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.YamlDataStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The portals of {@code data/portals.yml}: walking into one runs its actions, the same actions as
 * everywhere else. If a {@code connect} action of it cannot send the player away (the server is full or
 * down), or the player may not use it, they are pushed back out, so they are never stuck inside.
 *
 * <p>Corners are chosen with {@code /portal pos1} and {@code pos2} where the admin stands, or with the
 * wand: hitting a block sets the first corner, right-clicking one the second.
 */
public final class PortalService implements RegionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortalService.class);
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final String FILE_NAME = "portals.yml";
    private static final String HEADER = """
            Portals, normally made in game with /portal. See docs/movement-and-portals.md.

            Each portal is a box of blocks (from and to are its corners) that runs its actions for every
            player who walks in.""";
    /** Marks the selection wand. */
    public static final Tag<Boolean> WAND_TAG = Tag.Boolean("lobby_portal_wand");
    /** How hard a player is pushed back out, in blocks per second. */
    private static final double PUSH_SPEED = 8;
    private static final double PUSH_UP = 4;

    /** What {@link #create} did. */
    public enum CreateResult {
        CREATED, BAD_NAME, EXISTS, NO_SELECTION, TOO_BIG
    }

    private final YamlDataStore<Portal> store;
    private final RegionTracker regions;
    private final ActionServices actions;
    private final LobbyText text;
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    private final Map<UUID, Point[]> selections = new ConcurrentHashMap<>();

    private PortalService(YamlDataStore<Portal> store, RegionTracker regions, ActionServices actions, LobbyText text) {
        this.store = store;
        this.regions = regions;
        this.actions = actions;
        this.text = text;
    }

    /** Loads the file. Blocking (it reads a file), so call it at startup. */
    public static PortalService start(Path dataDir, RegionTracker regions, ActionServices actions, LobbyText text) {
        YamlDataStore<Portal> store = new YamlDataStore<>(dataDir.resolve("data").resolve(FILE_NAME), HEADER,
                new PortalCodec());
        PortalService service = new PortalService(store, regions, actions, text);
        regions.listen(Portal.OWNER, service);
        service.loadAll();
        return service;
    }

    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerStartDiggingEvent.class, event -> {
            if (holdsWand(event.getPlayer())) {
                event.setCancelled(true);
                select(event.getPlayer(), 0, event.getBlockPosition());
            }
        });
        node.addListener(PlayerBlockInteractEvent.class, event -> {
            if (event.getHand() == PlayerHand.MAIN && holdsWand(event.getPlayer())) {
                event.setCancelled(true);
                event.setBlockingItemUse(true);
                select(event.getPlayer(), 1, event.getBlockPosition());
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> selections.remove(event.getPlayer().getUuid()));
    }

    /** Reads the file again. Used by {@code /lobby reload}. */
    public synchronized void reload() {
        portals.clear();
        loadAll();
    }

    private synchronized void loadAll() {
        portals.putAll(store.load());
        applyRegions();
        LOGGER.info("Portals: {} loaded from {}{}", portals.size(), FILE_NAME,
                store.brokenEntries().isEmpty() ? "" : " (" + store.brokenEntries().size() + " broken, see above)");
    }

    private void applyRegions() {
        regions.setRegions(Portal.OWNER, portals.values().stream().map(Portal::region).toList());
    }

    @Override
    public void entered(Player player, Region region) {
        Portal portal;
        synchronized (this) {
            portal = portals.get(region.name());
        }
        if (portal == null) {
            return;
        }
        // The move into the portal has not been applied yet, so this is where the player came from.
        Pos before = player.getPosition();
        if (!portal.permission().isEmpty() && !actions.permissions().hasPermission(player, portal.permission())) {
            player.sendMessage(text.message(MessageKey.PORTAL_NO_PERMISSION, player));
            pushBack(player, before, region);
            return;
        }
        portal.actions().run(player, actions, "portal '" + portal.name() + "'", () -> pushBack(player, before, region));
    }

    /** Puts the player back where they stepped in, and away from the portal. */
    private static void pushBack(Player player, Pos before, Region region) {
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            if (!player.isOnline()) {
                return;
            }
            Pos now = player.getPosition();
            double centreX = (region.minX() + region.maxX() + 1) / 2.0;
            double centreZ = (region.minZ() + region.maxZ() + 1) / 2.0;
            Vec away = new Vec(before.x() - centreX, 0, before.z() - centreZ);
            Vec push = away.isZero() ? Vec.ZERO : away.normalize().mul(PUSH_SPEED);
            player.teleport(before.withView(now.yaw(), now.pitch())).thenRun(() -> player.setVelocity(push.withY(PUSH_UP)));
        });
    }

    /** Sets corner {@code index} (0 or 1) of a player's selection and tells them. */
    public void select(Player player, int index, Point block) {
        Point[] corners = selections.computeIfAbsent(player.getUuid(), ignored -> new Point[2]);
        corners[index] = new Vec(block.blockX(), block.blockY(), block.blockZ());
        player.sendMessage(text.message(MessageKey.PORTAL_POSITION_SET, player, Messages.text("number", index + 1),
                Messages.text("x", block.blockX()), Messages.text("y", block.blockY()), Messages.text("z", block.blockZ())));
    }

    /** Makes a portal from the player's selection and saves it. */
    public synchronized CreateResult create(Player player, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!NAME.matcher(key).matches()) {
            return CreateResult.BAD_NAME;
        }
        if (portals.containsKey(key)) {
            return CreateResult.EXISTS;
        }
        Point[] corners = selections.get(player.getUuid());
        if (corners == null || corners[0] == null || corners[1] == null) {
            return CreateResult.NO_SELECTION;
        }
        Region region = Region.between(Portal.OWNER, key, corners[0], corners[1]);
        if (region.volume() > Portal.MAX_VOLUME) {
            return CreateResult.TOO_BIG;
        }
        portals.put(key, Portal.create(key, region));
        changed();
        return CreateResult.CREATED;
    }

    /** Replaces a portal with a changed copy and saves. */
    public synchronized void update(Portal portal) {
        portals.put(portal.name(), portal);
        changed();
    }

    /** Removes a portal and saves. True if it existed. */
    public synchronized boolean delete(String name) {
        boolean removed = portals.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            changed();
        }
        return removed;
    }

    public synchronized @Nullable Portal get(String name) {
        return portals.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized List<Portal> all() {
        return new ArrayList<>(portals.values());
    }

    public synchronized int count() {
        return portals.size();
    }

    private void changed() {
        applyRegions();
        store.save(new LinkedHashMap<>(portals));
    }

    /** The selection wand: an item that marks corners instead of breaking or using blocks. */
    public ItemStack wand(Player player) {
        return ItemStack.builder(Material.BLAZE_ROD)
                .customName(text.message(MessageKey.PORTAL_WAND_NAME, player))
                .lore(List.<Component>of(text.message(MessageKey.PORTAL_WAND_LORE, player)))
                .glowing()
                .build()
                .withTag(WAND_TAG, true);
    }

    private static boolean holdsWand(Player player) {
        ItemStack held = player.getInventory().getItemStack(player.getHeldSlot());
        return Boolean.TRUE.equals(held.getTag(WAND_TAG));
    }

    /** Waits until everything is saved and stops the writer. */
    public void shutdown() {
        store.close();
    }

    public Path file() {
        return store.file();
    }
}
