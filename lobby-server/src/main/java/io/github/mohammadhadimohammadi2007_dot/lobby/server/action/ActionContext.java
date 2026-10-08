package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import net.minestom.server.entity.Player;

/**
 * Who an action list runs for and with which services.
 *
 * @param player   the player who clicked, walked in, used an item...
 * @param services shared lobby services
 * @param source   what started it, for log messages, e.g. {@code npc 'guide'}
 */
public record ActionContext(Player player, ActionServices services, String source) {
}
