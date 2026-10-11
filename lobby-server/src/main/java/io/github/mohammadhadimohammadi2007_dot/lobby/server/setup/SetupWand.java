package io.github.mohammadhadimohammadi2007_dot.lobby.server.setup;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectClicks;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.MeasuredThread;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.Npc;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
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
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The setup wand ({@code /setup-lobby wand}, also {@code /portal wand}), one tool for everything set up in
 * the world:
 * <ul>
 *   <li>hit a block: corner 1 of the selection; right-click a block: corner 2. Portals, and later parkour,
 *       PvP and elytra regions, are made from this selection;</li>
 *   <li>click an NPC or hologram: it becomes the selected object (no actions run), for the edit
 *       commands and menus;</li>
 *   <li>sneak and click a block inside a region (a portal...): that region becomes the selected object.</li>
 * </ul>
 * The selection is outlined with particles that only the admin holding the wand sees. The particles are
 * sent from the {@code lobby-effects} thread, never the tick thread.
 */
public final class SetupWand {

    /** Marks the wand item. */
    public static final Tag<Boolean> WAND_TAG = Tag.Boolean("lobby_setup_wand");
    /** The tag of the old portal wand, which still works. */
    private static final Tag<Boolean> OLD_PORTAL_WAND_TAG = Tag.Boolean("lobby_portal_wand");
    /** How often the outline is drawn, in ticks. */
    private static final int OUTLINE_TICKS = 10;
    /** Most particles in one outline; a big box gets fewer points per edge. */
    static final int MAX_OUTLINE_POINTS = 240;

    /** What kind of thing an object selection is. */
    public enum Kind { NPC, HOLOGRAM, REGION }

    /**
     * A selected object.
     *
     * @param owner for regions: the region owner (for example {@code portal}); otherwise empty
     * @param at    where it is, for the outline
     */
    public record Selected(Kind kind, String owner, String name, Point at) {
    }

    /** A player's two corners; either may be missing. */
    public record Corners(@Nullable Point first, @Nullable Point second) {

        public boolean complete() {
            return first != null && second != null;
        }
    }

    private final LobbyText text;
    private final RegionTracker regions;
    private final Function<String, Optional<Point>> objectPosition;
    private final MeasuredThread effects;
    private final Map<UUID, Corners> corners = new ConcurrentHashMap<>();
    private final Map<UUID, Selected> selected = new ConcurrentHashMap<>();
    private @Nullable Task outline;

    /**
     * @param objectPosition where an NPC or hologram is, by the renderer's object name (for the outline)
     * @param effects        the thread particles are sent from
     */
    public SetupWand(LobbyText text, RegionTracker regions, Function<String, Optional<Point>> objectPosition,
                     MeasuredThread effects) {
        this.text = text;
        this.regions = regions;
        this.objectPosition = objectPosition;
        this.effects = effects;
    }

    /** The wand item. */
    public ItemStack item(Player player) {
        return ItemStack.builder(Material.BLAZE_ROD)
                .customName(text.message(MessageKey.WAND_NAME, player))
                .lore(List.<Component>of(text.message(MessageKey.WAND_LORE_1, player),
                        text.message(MessageKey.WAND_LORE_2, player), text.message(MessageKey.WAND_LORE_3, player)))
                .glowing()
                .build()
                .withTag(WAND_TAG, true);
    }

    /** Gives the wand and says how to use it. */
    public void give(Player player) {
        player.getInventory().addItemStack(item(player));
        player.sendMessage(text.message(MessageKey.WAND_GIVEN, player));
    }

    public void register(EventNode<PlayerEvent> node, ClientObjectClicks clicks) {
        node.addListener(PlayerStartDiggingEvent.class, event -> {
            if (holds(event.getPlayer())) {
                event.setCancelled(true);
                block(event.getPlayer(), 0, event.getBlockPosition());
            }
        });
        node.addListener(PlayerBlockInteractEvent.class, event -> {
            if (event.getHand() == PlayerHand.MAIN && holds(event.getPlayer())) {
                event.setCancelled(true);
                event.setBlockingItemUse(true);
                block(event.getPlayer(), 1, event.getBlockPosition());
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> {
            corners.remove(event.getPlayer().getUuid());
            selected.remove(event.getPlayer().getUuid());
        });
        // First in line: with the wand, a click selects the NPC or hologram instead of running its actions.
        clicks.addFirstHandler((player, objectName, type) -> {
            if (!holds(player)) {
                return false;
            }
            Kind kind = objectName.startsWith(Npc.OBJECT_PREFIX) ? Kind.NPC : Kind.HOLOGRAM;
            String name = kind == Kind.NPC ? objectName.substring(Npc.OBJECT_PREFIX.length()) : objectName;
            Point at = objectPosition.apply(objectName).orElse(player.getPosition());
            selected.put(player.getUuid(), new Selected(kind, "", name, at));
            player.sendMessage(text.message(kind == Kind.NPC ? MessageKey.WAND_SELECTED_NPC : MessageKey.WAND_SELECTED_HOLOGRAM,
                    player, Messages.text("name", name)));
            return true;
        });
        outline = MinecraftServer.getSchedulerManager().buildTask(() -> effects.execute(this::drawOutlines))
                .repeat(TaskSchedule.tick(OUTLINE_TICKS))
                .schedule();
    }

    private void block(Player player, int index, Point block) {
        if (index == 0 && player.isSneaking()) {
            for (Region region : regions.regionsAt(block)) {
                selected.put(player.getUuid(), new Selected(Kind.REGION, region.owner(), region.name(),
                        new Vec(region.minX(), region.minY(), region.minZ())));
                player.sendMessage(text.message(MessageKey.WAND_SELECTED_REGION, player,
                        Messages.text("kind", region.owner()), Messages.text("name", region.name())));
                return;
            }
        }
        select(player, index, block);
    }

    /** Sets corner {@code index} (0 or 1) and tells the player what is still missing. */
    public void select(Player player, int index, Point point) {
        Point block = new Vec(point.blockX(), point.blockY(), point.blockZ());
        Corners updated = corners.compute(player.getUuid(), (id, old) -> {
            Corners current = old == null ? new Corners(null, null) : old;
            return index == 0 ? new Corners(block, current.second()) : new Corners(current.first(), block);
        });
        MessageKey key = updated.complete() ? MessageKey.WAND_CORNER_DONE : MessageKey.WAND_CORNER_SET;
        long size = updated.complete() ? Region.between("selection", "selection", updated.first(), updated.second()).volume() : 0;
        player.sendMessage(text.message(key, player, Messages.text("number", index + 1),
                Messages.text("x", block.blockX()), Messages.text("y", block.blockY()), Messages.text("z", block.blockZ()),
                Messages.text("other", index == 0 ? 2 : 1), Messages.text("blocks", size)));
    }

    /** The player's corners (may be incomplete). */
    public Corners corners(Player player) {
        return corners.getOrDefault(player.getUuid(), new Corners(null, null));
    }

    /** The region between the player's corners, or empty if a corner is missing. */
    public Optional<Region> region(Player player, String owner, String name) {
        Corners current = corners(player);
        return current.complete() ? Optional.of(Region.between(owner, name, current.first(), current.second()))
                : Optional.empty();
    }

    /** The object the player clicked with the wand, if any. */
    public Optional<Selected> selected(Player player) {
        return Optional.ofNullable(selected.get(player.getUuid()));
    }

    /** True if the player holds the wand (or the old portal wand) in their main hand. */
    public static boolean holds(Player player) {
        ItemStack held = player.getInventory().getItemStack(player.getHeldSlot());
        return Boolean.TRUE.equals(held.getTag(WAND_TAG)) || Boolean.TRUE.equals(held.getTag(OLD_PORTAL_WAND_TAG));
    }

    /** Draws every wand holder's selection, for them only. On the effects thread. */
    private void drawOutlines() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!holds(player)) {
                continue;
            }
            Corners current = corners.get(player.getUuid());
            if (current != null && current.complete()) {
                for (Vec point : outline(current.first(), current.second())) {
                    player.sendPacket(new ParticlePacket(Particle.HAPPY_VILLAGER, point, Vec.ZERO, 0, 1));
                }
            } else if (current != null) {
                Point single = current.first() != null ? current.first() : current.second();
                for (Vec point : outline(single, single)) {
                    player.sendPacket(new ParticlePacket(Particle.HAPPY_VILLAGER, point, Vec.ZERO, 0, 1));
                }
            }
            Selected object = selected.get(player.getUuid());
            if (object != null) {
                Point at = object.kind() == Kind.REGION ? object.at()
                        : objectPosition.apply((object.kind() == Kind.NPC ? Npc.OBJECT_PREFIX : "") + object.name())
                        .orElse(object.at());
                for (int step = 0; step < 6; step++) {
                    player.sendPacket(new ParticlePacket(Particle.END_ROD, new Pos(at.x(), at.y() + step * 0.4, at.z()),
                            Vec.ZERO, 0, 1));
                }
            }
        }
    }

    /** Points along the 12 edges of the box between two blocks (inclusive), at most {@link #MAX_OUTLINE_POINTS}. */
    static List<Vec> outline(Point a, Point b) {
        double minX = Math.min(a.blockX(), b.blockX());
        double minY = Math.min(a.blockY(), b.blockY());
        double minZ = Math.min(a.blockZ(), b.blockZ());
        double maxX = Math.max(a.blockX(), b.blockX()) + 1;
        double maxY = Math.max(a.blockY(), b.blockY()) + 1;
        double maxZ = Math.max(a.blockZ(), b.blockZ()) + 1;
        double totalLength = 4 * ((maxX - minX) + (maxY - minY) + (maxZ - minZ));
        double step = Math.max(0.5, totalLength / MAX_OUTLINE_POINTS);
        List<Vec> points = new ArrayList<>();
        for (double y : new double[]{minY, maxY}) {
            for (double z : new double[]{minZ, maxZ}) {
                line(points, new Vec(minX, y, z), new Vec(maxX, y, z), step);
            }
        }
        for (double x : new double[]{minX, maxX}) {
            for (double z : new double[]{minZ, maxZ}) {
                line(points, new Vec(x, minY, z), new Vec(x, maxY, z), step);
            }
            for (double y : new double[]{minY, maxY}) {
                line(points, new Vec(x, y, minZ), new Vec(x, y, maxZ), step);
            }
        }
        return points.size() > MAX_OUTLINE_POINTS ? points.subList(0, MAX_OUTLINE_POINTS) : points;
    }

    private static void line(List<Vec> points, Vec from, Vec to, double step) {
        double length = from.distance(to);
        int count = Math.max(1, (int) Math.round(length / step));
        for (int i = 0; i <= count; i++) {
            points.add(from.add(to.sub(from).mul((double) i / count)));
        }
    }

    public void shutdown() {
        if (outline != null) {
            outline.cancel();
        }
    }
}
