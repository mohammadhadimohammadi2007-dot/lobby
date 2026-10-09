package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObject;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.EntityPart;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.List;
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
        return HologramText.variant(data, services.text(), viewer, services.legacyClient().test(viewer), false);
    }

    @Override
    public int updateIntervalTicks() {
        return data.effectiveUpdateIntervalTicks();
    }

    @Override
    public List<EntityPart> render(Object key, Player viewer) {
        boolean legacy = key instanceof HologramText.Variant variant && variant.legacy();
        return HologramText.parts(data, services.text(), viewer, legacy);
    }
}
