package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import net.minestom.server.entity.Player;

/**
 * Who an action list runs for and with which services.
 *
 * @param player   the player who clicked, walked in, used an item...
 * @param services shared lobby services
 * @param source   what started it, for log messages, e.g. {@code npc 'guide'}
 * @param onConnectFailed run if a {@code connect} action of the list could not send the player away, for
 *                        example a portal pushing them back out
 */
public record ActionContext(Player player, ActionServices services, String source, Runnable onConnectFailed) {

    public ActionContext(Player player, ActionServices services, String source) {
        this(player, services, source, () -> { });
    }
}
