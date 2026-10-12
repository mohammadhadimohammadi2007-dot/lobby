package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import net.minestom.server.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Runs commands on the proxy as a player: {@code proxy_command} actions, and {@code player_command} actions
 * whose command the lobby does not have (such as {@code server bedwars}). Through the bridge behind Velocity;
 * elsewhere the player is told it is not available.
 *
 * <p>Only actions an admin configured come here. Commands a player types never do.
 */
public interface ProxyCommands {

    /** Longest command sent to the proxy; more than a player can type in chat. */
    int MAX_LENGTH = 256;

    /**
     * Runs {@code command} on the proxy as {@code player}, telling the player if it did not work.
     *
     * @param command without the leading {@code /}
     * @param source  what asked, for log messages, e.g. {@code NPC 'bedwars'}
     * @return completes with true if the proxy ran it
     */
    CompletableFuture<Boolean> run(Player player, String command, String source);

    /**
     * The command as it is sent: without the leading {@code /}, line breaks and other control characters turned
     * into spaces (a placeholder could contain them), or {@code null} if nothing is left or it is too long.
     */
    static String clean(String command) {
        StringBuilder cleaned = new StringBuilder(command.length());
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            cleaned.append(Character.isISOControl(c) ? ' ' : c);
        }
        String value = cleaned.toString().strip();
        if (value.startsWith("/")) {
            value = value.substring(1).strip();
        }
        return value.isEmpty() || value.length() > MAX_LENGTH ? null : value;
    }
}
