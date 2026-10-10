package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.display.DisplayConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.movement.MovementConfig;

import java.util.List;

/**
 * All config files loaded together at one moment.
 *
 * @param config       config.yml
 * @param integrations integrations.yml
 * @param chat         chat.yml
 * @param menus        menus.yml
 * @param display      display.yml
 * @param hotbar       hotbar.yml
 * @param movement     movement.yml
 * @param messages     messages.yml
 * @param warnings     non-fatal problems found while loading, ready to be logged
 */
public record ConfigSnapshot(LobbyConfig config, IntegrationsConfig integrations, ChatConfig chat, MenuConfig menus,
                             DisplayConfig display, HotbarConfig hotbar, MovementConfig movement, Messages messages,
                             List<String> warnings) {
    public ConfigSnapshot {
        warnings = List.copyOf(warnings);
    }
}
