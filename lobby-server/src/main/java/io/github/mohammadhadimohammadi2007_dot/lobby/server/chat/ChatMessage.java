package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.RenderedMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One chat message on its way through the pipeline. Each stage reads and fills in parts of it.
 * Only touched by the chat thread.
 */
public final class ChatMessage {

    private final String id;
    private final UUID senderId;
    private final String senderName;
    private final Player sender;
    private final String original;
    private final long receivedMillis;

    private String text;
    private String normalized = "";
    private ChatConfig.Format format;
    private PlayerMeta senderMeta = PlayerMeta.EMPTY;
    private final List<String> modifications = new ArrayList<>();
    private final Map<UUID, String> mentionedNames = new HashMap<>();
    private boolean mentionEveryone;
    private boolean notifyMentions = true;
    private boolean styling;
    private boolean emojis;
    private RenderedMessage rendered;

    /**
     * @param id       short id shown to staff
     * @param original exactly what was typed
     */
    public ChatMessage(String id, Player sender, String original) {
        this.id = id;
        this.senderId = sender.getUuid();
        this.senderName = sender.getUsername();
        this.sender = sender;
        this.original = original;
        this.text = original;
        this.receivedMillis = System.currentTimeMillis();
    }

    public String id() {
        return id;
    }

    public UUID senderId() {
        return senderId;
    }

    public String senderName() {
        return senderName;
    }

    public Player sender() {
        return sender;
    }

    /** Exactly what the player typed. This is what gets logged. */
    public String original() {
        return original;
    }

    /** The text players will see, after anti-spam and filter changes. */
    public String text() {
        return text;
    }

    /** Changes the visible text and remembers why, for the log. */
    public void modify(String newText, String reason) {
        if (!newText.equals(text)) {
            text = newText;
            modifications.add(reason);
        }
    }

    public List<String> modifications() {
        return modifications;
    }

    public String normalized() {
        return normalized;
    }

    public void normalized(String value) {
        normalized = value;
    }

    public long receivedMillis() {
        return receivedMillis;
    }

    public ChatConfig.Format format() {
        return format;
    }

    public void format(ChatConfig.Format value) {
        format = value;
    }

    public PlayerMeta senderMeta() {
        return senderMeta;
    }

    public void senderMeta(PlayerMeta value) {
        senderMeta = value;
    }

    /** Players mentioned in the message: id to the name as it should be highlighted. */
    public Map<UUID, String> mentionedNames() {
        return mentionedNames;
    }

    public boolean mentionEveryone() {
        return mentionEveryone;
    }

    public void mentionEveryone(boolean value) {
        mentionEveryone = value;
    }

    /** False when the sender is on mention cooldown: names are still highlighted, but nobody is pinged. */
    public boolean notifyMentions() {
        return notifyMentions;
    }

    public void notifyMentions(boolean value) {
        notifyMentions = value;
    }

    /** True if the sender may style the message (lobby.chat.formatting). */
    public boolean styling() {
        return styling;
    }

    public void styling(boolean value) {
        styling = value;
    }

    /** True if {@code :emoji:} shortcodes are turned into symbols for this message. */
    public boolean emojis() {
        return emojis;
    }

    public void emojis(boolean value) {
        emojis = value;
    }

    public RenderedMessage rendered() {
        return rendered;
    }

    public void rendered(RenderedMessage value) {
        rendered = value;
    }
}
