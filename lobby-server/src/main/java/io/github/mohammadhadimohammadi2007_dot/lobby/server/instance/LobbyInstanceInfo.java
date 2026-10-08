package io.github.mohammadhadimohammadi2007_dot.lobby.server.instance;

import net.minestom.server.entity.Player;

/**
 * What the lobby instances look like from the outside, for placeholders, menus and the chat system.
 * {@link #SINGLE} is the answer for a server with one lobby, which is the default.
 */
public interface LobbyInstanceInfo {

    /** How many lobby instances this server has (at least 1). */
    int count();

    /** The number (1-based) of the instance {@code player} is in, or 0 if they are somewhere else. */
    int numberOf(Player player);

    /** How many players are in instance {@code number}, or 0 if there is no such instance. */
    int onlineIn(int number);

    /** One lobby that holds every player. */
    LobbyInstanceInfo SINGLE = new LobbyInstanceInfo() {
        @Override
        public int count() {
            return 1;
        }

        @Override
        public int numberOf(Player player) {
            return 1;
        }

        @Override
        public int onlineIn(int number) {
            return number == 1 ? net.minestom.server.MinecraftServer.getConnectionManager().getOnlinePlayerCount() : 0;
        }
    };
}
