package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;

/** Lets tests in other packages run the whole lobby inside a test server. */
public final class LobbyServerTestAccess {

    private LobbyServerTestAccess() {
    }

    /**
     * Builds the lobby and returns it; stop it with {@link #stop(LobbyServer)}. The addons folder is read too (it is
     * empty in these tests), so every test of the whole lobby runs with the addon system as a server without
     * addons has it.
     */
    public static LobbyServer start(ConfigManager config) {
        LobbyServer lobby = new LobbyServer(config);
        lobby.loadAddons();
        lobby.build();
        return lobby;
    }

    public static void stop(LobbyServer lobby) {
        lobby.stopFeatures();
    }

    /** Builds a lobby made by the test (for example after {@link LobbyServer#loadAddons()}). */
    public static void build(LobbyServer lobby) {
        lobby.build();
    }

    /** Builds the lobby as {@link LobbyServer} does; the returned task stops it again. */
    public static Runnable build(ConfigManager config) {
        LobbyServer lobby = new LobbyServer(config);
        lobby.loadAddons();
        lobby.build();
        return lobby::stopFeatures;
    }
}
