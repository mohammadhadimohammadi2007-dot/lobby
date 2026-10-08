package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ComponentTransforms;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObject;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.EntityPart;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One hologram, shown to the players near it with packets only.
 *
 * <p>Viewers are grouped by what they would actually see: which client generation they are on, and, if
 * the text has placeholders, who they are. A hologram without placeholders is therefore built once for
 * the whole server, however many players read it.
 */
public final class Hologram implements ClientObject {

    /** The permission a hologram in {@code visibility: permission} mode needs when it has no own one. */
    public static final String SEE_PERMISSION_PREFIX = "lobby.hologram.see.";

    /**
     * What decides whether two viewers can share one rendered hologram: their client generation,
     * whether their texts are reshaped for right-to-left reading, and, only for text that really
     * differs per player, who they are.
     */
    record Variant(boolean legacy, boolean reshaped, @Nullable UUID viewer) {
    }

    /**
     * Everything a hologram needs from the rest of the lobby.
     *
     * @param legacyClient true for a viewer whose client is older than 1.19.4 and has no display
     *                     entities (normally {@code bridge.capabilities(player).legacy()})
     * @param scope        which worlds the holograms belong to
     */
    public record Services(LobbyText text, PermissionService permissions, Predicate<Player> legacyClient,
                           WorldScope scope) {
    }

    private final HologramData data;
    private final Services services;

    public Hologram(HologramData data, Services services) {
        this.data = data;
        this.services = services;
    }

    /** The stored hologram behind this one. */
    public HologramData data() {
        return data;
    }

    @Override
    public String name() {
        return data.name();
    }

    @Override
    public Pos position() {
        return data.position();
    }

    @Override
    public WorldScope scope() {
        return services.scope();
    }

    @Override
    public double viewDistance() {
        return data.viewDistance();
    }

    @Override
    public boolean visibleTo(Player viewer) {
        return switch (data.visibility()) {
            case ALL -> hasPermission(viewer);
            // Without a permission of its own, the hologram's name decides the permission.
            case PERMISSION -> data.permission().isEmpty()
                    ? services.permissions().hasPermission(viewer, SEE_PERMISSION_PREFIX + data.name())
                    : hasPermission(viewer);
            case MANUAL -> data.manualViewers().contains(viewer.getUuid()) && hasPermission(viewer);
        };
    }

    private boolean hasPermission(Player viewer) {
        String permission = data.permission();
        return permission.isEmpty() || services.permissions().hasPermission(viewer, permission);
    }

    @Override
    public Object variantKey(Player viewer) {
        boolean legacy = services.legacyClient().test(viewer);
        // Persian text has two versions, not one per player: the fix is a setting with two values.
        boolean reshaped = data.reshapeMatters() && services.text().transformsFor(viewer);
        // Only text that really differs per player gets its own render; a player count does not.
        return new Variant(legacy, reshaped, data.textScope().perPlayer() ? viewer.getUuid() : null);
    }

    @Override
    public int updateIntervalTicks() {
        return data.effectiveUpdateIntervalTicks();
    }

    @Override
    public List<EntityPart> render(Object key, Player viewer) {
        boolean legacy = key instanceof Variant variant && variant.legacy();
        List<Component> lines = data.type() == HologramType.TEXT ? lines(viewer, legacy) : List.of();
        return legacy ? HologramParts.legacy(data, lines) : HologramParts.modern(data, lines);
    }

    private List<Component> lines(Player viewer, boolean legacy) {
        List<String> frame = currentFrame();
        List<Component> rendered = new ArrayList<>(frame.size());
        for (String line : frame) {
            Component component = services.text().render(line, viewer);
            // Old clients cannot show the RGB colours, so they are mapped to the 16 they know.
            rendered.add(legacy ? ComponentTransforms.downsampleColors(component) : component);
        }
        return rendered;
    }

    /**
     * The frame to show now. It comes from the clock, not from a counter, so every group of viewers is
     * on the same frame even though they are rendered separately.
     */
    private List<String> currentFrame() {
        List<List<String>> frames = data.frames();
        if (frames.size() == 1) {
            return frames.getFirst();
        }
        long frameMillis = (long) Math.max(1, data.effectiveUpdateIntervalTicks()) * MinecraftServer.TICK_MS;
        int index = (int) Math.floorMod(System.currentTimeMillis() / frameMillis, frames.size());
        return frames.get(index);
    }
}
