package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

/** Permission nodes of the chat system. Listed in docs/chat.md. */
public final class ChatPermissions {

    /** Style messages with the tags allowed in chat.yml (player-formatting). */
    public static final String FORMATTING = "lobby.chat.formatting";
    /** Use {@code :emoji:} shortcodes. */
    public static final String EMOJI = "lobby.chat.emoji";
    /** Use {@code @everyone}. */
    public static final String MENTION_EVERYONE = "lobby.chat.mention.everyone";
    /** Skip cooldown, duplicate, caps, length and new-player checks. */
    public static final String BYPASS_SPAM = "lobby.chat.bypass.spam";
    /** Skip the word, link and IP filter. */
    public static final String BYPASS_FILTER = "lobby.chat.bypass.filter";
    /** Chat while the chat is locked. */
    public static final String BYPASS_LOCK = "lobby.chat.bypass.lock";
    /** Ignore slow mode. */
    public static final String BYPASS_SLOWMODE = "lobby.chat.bypass.slowmode";
    /** Get filter notifications. */
    public static final String NOTIFY = "lobby.chat.notify";
    /** See message ids in the name hover (needed for /chat delete). */
    public static final String STAFF = "lobby.chat.staff";
    /** Prefix of per-rank cooldowns: {@code lobby.chat.cooldown.<seconds>}. */
    public static final String COOLDOWN_PREFIX = "lobby.chat.cooldown.";
    /** Prefix of format permissions: {@code lobby.chat.format.<name>}. */
    public static final String FORMAT_PREFIX = "lobby.chat.format.";

    public static final String COMMAND_CHAT = "lobby.command.chat";
    public static final String COMMAND_CHANNEL = "lobby.command.ch";
    public static final String COMMAND_IGNORE = "lobby.command.ignore";
    public static final String COMMAND_MSG = "lobby.command.msg";
    public static final String COMMAND_CLEAR = "lobby.command.chat.clear";
    public static final String COMMAND_LOCK = "lobby.command.chat.lock";
    public static final String COMMAND_DELETE = "lobby.command.chat.delete";
    public static final String COMMAND_SPY = "lobby.command.chat.spy";
    public static final String COMMAND_SLOWMODE = "lobby.command.slowmode";

    private ChatPermissions() {
    }
}
