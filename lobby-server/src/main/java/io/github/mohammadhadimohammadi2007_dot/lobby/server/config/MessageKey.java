package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

/** Every message in {@code messages.yml}. The bundled file must contain all of them. */
public enum MessageKey {
    PREFIX("prefix"),
    NO_PERMISSION("no-permission"),
    PLAYERS_ONLY("players-only"),
    SPAWN_TELEPORTED("spawn-teleported"),
    LOBBY_USAGE("lobby-usage"),
    RELOAD_DONE("reload-done"),
    RELOAD_RESTART_NEEDED("reload-restart-needed"),
    RELOAD_FAILED("reload-failed"),
    RELOAD_WARNINGS("reload-warnings"),
    SETSPAWN_DONE("setspawn-done"),
    SETSPAWN_FAILED("setspawn-failed"),
    INFO("info"),
    KICK_SERVER_FULL("kick-server-full"),
    KICK_BANNED("kick-banned"),
    BAN_NEVER_EXPIRES("ban-never-expires");

    private final String path;

    MessageKey(String path) {
        this.path = path;
    }

    /** The key used in messages.yml. */
    public String path() {
        return path;
    }
}
