package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.settings.AddonSettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.CommandWhitelist;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.command.CommandManager;

/**
 * The parts of the running lobby the addon API is built on, handed to the {@link AddonManager} once they
 * exist (after the lobby has started, before addons are enabled).
 *
 * @param reloadData reads every config and data file again, as {@code /setup-lobby reload} does; used after
 *                   an addon was reloaded, so entries using its action types and placeholders are read again
 */
public record AddonServices(LobbyText text, CommandManager commands, CommandWhitelist whitelist,
                            PermissionService permissions, ActionServices actions, MenuService menus,
                            HologramService holograms, NpcService npcs, BridgeService bridge,
                            AddonSettingsStore settings, Runnable reloadData) {
}
