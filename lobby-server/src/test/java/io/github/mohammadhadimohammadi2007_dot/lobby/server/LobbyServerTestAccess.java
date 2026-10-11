package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;

/** Lets tests in other packages run the whole lobby inside a test server. */
public final class LobbyServerTestAccess {

    private LobbyServerTestAccess() {
    }

    /** Builds the lobby and returns it; stop it with {@link #stop(LobbyServer)}. */
    public static LobbyServer start(ConfigManager config) {
        LobbyServer lobby = new LobbyServer(config);
        lobby.build();
        return lobby;
    }

    public static void stop(LobbyServer lobby) {
        lobby.stopFeatures();
    }

    /** Builds the lobby as {@link LobbyServer} does; the returned task stops it again. */
    public static Runnable build(ConfigManager config) {
        LobbyServer lobby = new LobbyServer(config);
        lobby.build();
        return lobby::stopFeatures;
    }
}
