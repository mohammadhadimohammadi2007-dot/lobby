package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Text the way the lobby writes its own messages: MiniMessage, the lobby's placeholders, and {@code <prefix>} for the
 * prefix of messages.yml. Use it so an addon's messages look like the rest of the lobby (and follow the server
 * owner's prefix). Colors are shown to old clients as the nearest of their 16. Any thread.
 */
@NonExtendable
public interface Texts {

    /**
     * {@code miniMessage} with placeholders filled in for {@code player} ({@code null}: global ones only).
     * Never pass text a player typed as MiniMessage: they could add colors, clicks and hovers.
     */
    Component render(String miniMessage, @Nullable Player player);

    /** Renders {@code miniMessage} for the player and sends it to them in chat. */
    default void send(Player player, String miniMessage) {
        player.sendMessage(render(miniMessage, player));
    }

    /** Renders {@code miniMessage} for each online player and sends it to them in chat. */
    void broadcast(String miniMessage);
}
