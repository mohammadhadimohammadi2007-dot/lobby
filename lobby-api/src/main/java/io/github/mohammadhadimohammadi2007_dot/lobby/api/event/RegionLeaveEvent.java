package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/** A player walked out of a region of the lobby map. Tick thread. */
public final class RegionLeaveEvent extends LobbyPlayerEvent {

    private final String owner;
    private final String region;

    public RegionLeaveEvent(Player player, String owner, String region) {
        super(player);
        this.owner = owner;
        this.region = region;
    }

    /** What the region belongs to, e.g. {@code portal}. */
    public String owner() {
        return owner;
    }

    /** The region's name, unique within its owner. */
    public String region() {
        return region;
    }
}
