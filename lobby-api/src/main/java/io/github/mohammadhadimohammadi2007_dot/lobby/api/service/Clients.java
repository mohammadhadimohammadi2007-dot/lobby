package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import net.minestom.server.entity.Player;

/**
 * Which Minecraft version a player really plays on. Behind ViaVersion the lobby only knows this from the
 * proxy bridge; without it every player counts as the lobby's own version. Any thread.
 */
public interface Clients {

    /** The client's protocol version number, e.g. 47 for 1.8.x. */
    int protocolVersion(Player player);

    /** A readable version, e.g. {@code 1.8.x} or {@code 1.21.4}. */
    String versionName(Player player);

    /**
     * True for clients older than 1.19.4: no display entities (holograms are armor stands there), and on
     * 1.8 no off hand, no attack cooldown and fewer items.
     */
    boolean legacy(Player player);

    /** True if the bridge reported the version; false if it is only the lobby's own version as a guess. */
    boolean reported(Player player);
}
