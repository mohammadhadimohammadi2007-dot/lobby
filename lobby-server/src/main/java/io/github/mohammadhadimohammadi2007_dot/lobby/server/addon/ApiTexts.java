package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Texts;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Addons' text through the lobby's own renderer, so {@code <prefix>} and placeholders work as in messages.yml. */
final class ApiTexts implements Texts {

    private final LobbyText text;

    ApiTexts(LobbyText text) {
        this.text = text;
    }

    @Override
    public Component render(String miniMessage, @Nullable Player player) {
        return text.renderWithPrefix(miniMessage, player);
    }

    @Override
    public void broadcast(String miniMessage) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(render(miniMessage, player));
        }
    }
}
