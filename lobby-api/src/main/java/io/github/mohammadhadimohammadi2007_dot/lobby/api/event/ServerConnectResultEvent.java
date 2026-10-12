package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/**
 * What happened when the lobby tried to send a player to another server (an NPC, portal, menu, hotbar item
 * or an addon). Fired once the proxy answered, on any thread.
 */
public final class ServerConnectResultEvent extends LobbyPlayerEvent {

    /** How it went. */
    public enum Outcome {
        /** The player is on their way. Behind BungeeCord this is assumed, since the proxy never answers. */
        CONNECTED,
        /** The server (or every server of the group) is full. */
        FULL,
        /** The server (or every server of the group) is offline. */
        OFFLINE,
        /** The proxy does not know that server or group. */
        UNKNOWN,
        /** The proxy refused, or did not answer in time. */
        FAILED,
        /** There is no proxy to ask (standalone mode). */
        NOT_AVAILABLE
    }

    private final String target;
    private final boolean group;
    private final Outcome outcome;

    public ServerConnectResultEvent(Player player, String target, boolean group, Outcome outcome) {
        super(player);
        this.target = target;
        this.group = group;
        this.outcome = outcome;
    }

    /** The server or group name asked for. */
    public String target() {
        return target;
    }

    /** True if {@link #target()} is a group of servers. */
    public boolean group() {
        return group;
    }

    public Outcome outcome() {
        return outcome;
    }
}
