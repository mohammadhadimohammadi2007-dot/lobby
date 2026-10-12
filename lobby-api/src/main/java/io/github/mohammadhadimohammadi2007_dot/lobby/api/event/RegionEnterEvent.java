package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/**
 * A player walked into a region of the lobby map: a portal, jump pad or another feature's area. Tick thread.
 *
 * @see RegionLeaveEvent
 */
public final class RegionEnterEvent extends LobbyPlayerEvent {

    private final String owner;
    private final String region;

    public RegionEnterEvent(Player player, String owner, String region) {
        super(player);
        this.owner = owner;
        this.region = region;
    }

    /** What the region belongs to, e.g. {@code portal}. */
    public String owner() {
        return owner;
    }

    /** The region's name, unique within its owner, e.g. the portal's name. */
    public String region() {
        return region;
    }
}
