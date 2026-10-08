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
    BAN_NEVER_EXPIRES("ban-never-expires"),

    // Chat
    CHAT_MUTED("chat-muted"),
    CHAT_MUTED_COMMAND("chat-muted-command"),
    CHAT_COOLDOWN("chat-cooldown"),
    CHAT_DUPLICATE("chat-duplicate"),
    CHAT_TOO_LONG("chat-too-long"),
    CHAT_NEW_PLAYER_WAIT("chat-new-player-wait"),
    CHAT_NEW_PLAYER_MOVE("chat-new-player-move"),
    CHAT_SLOWMODE("chat-slowmode"),
    CHAT_LOCKED("chat-locked"),
    CHAT_BLOCKED("chat-blocked"),
    CHAT_WARNING("chat-warning"),
    CHAT_LINK_WARNING("chat-link-warning"),
    CHAT_AUTO_MUTED("chat-auto-muted"),
    CHAT_DISABLED_FOR_YOU("chat-disabled-for-you"),
    CHAT_STAFF_NOTIFY("chat-staff-notify"),
    CHAT_SPY("chat-spy"),
    CHAT_STAFF_HOVER("chat-staff-hover"),
    CHAT_STAFF_AUTO_MUTED("chat-staff-auto-muted"),
    CHANNEL_SWITCHED("channel-switched"),
    CHANNEL_UNKNOWN("channel-unknown"),
    CHANNEL_NO_PERMISSION("channel-no-permission"),
    CH_USAGE("ch-usage"),
    CHAT_USAGE("chat-usage"),
    CHAT_STAFF_USAGE("chat-staff-usage"),
    CHAT_HIDDEN("chat-hidden"),
    CHAT_SHOWN("chat-shown"),
    PERSIAN_ON("persian-on"),
    PERSIAN_OFF("persian-off"),
    MENTIONS_ON("mentions-on"),
    MENTIONS_OFF("mentions-off"),
    FEATURE_DISABLED("feature-disabled"),
    MENTION_ACTIONBAR("mention-actionbar"),
    IGNORE_ADDED("ignore-added"),
    IGNORE_REMOVED("ignore-removed"),
    IGNORE_SELF("ignore-self"),
    IGNORE_LIST("ignore-list"),
    IGNORE_EMPTY("ignore-empty"),
    IGNORE_USAGE("ignore-usage"),
    PLAYER_NOT_FOUND("player-not-found"),
    MSG_TO("msg-to"),
    MSG_FROM("msg-from"),
    MSG_NO_REPLY("msg-no-reply"),
    MSG_USAGE("msg-usage"),
    MSG_IGNORED("msg-ignored"),
    CHAT_CLEARED("chat-cleared"),
    CHAT_LOCKED_ON("chat-locked-on"),
    CHAT_LOCKED_OFF("chat-locked-off"),
    SLOWMODE_ON("slowmode-on"),
    SLOWMODE_OFF("slowmode-off"),
    SLOWMODE_USAGE("slowmode-usage"),
    MESSAGE_DELETED("message-deleted"),
    MESSAGE_NOT_FOUND("message-not-found"),
    SPY_ON("spy-on"),
    SPY_OFF("spy-off"),
    JOIN_MESSAGE("join-message"),
    QUIT_MESSAGE("quit-message");

    private final String path;

    MessageKey(String path) {
        this.path = path;
    }

    /** The key used in messages.yml. */
    public String path() {
        return path;
    }
}
