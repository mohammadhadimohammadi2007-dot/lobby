package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.EnumMap;
import java.util.Map;

/**
 * Player-facing texts from {@code messages.yml}, ready to be turned into chat components.
 *
 * <p>Example: {@code messages.render(MessageKey.SETSPAWN_FAILED, Messages.text("error", "disk full"))}.
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Map<MessageKey, String> templates;

    private Messages(Map<MessageKey, String> templates) {
        this.templates = templates;
    }

    /** Reads every {@link MessageKey} from messages.yml. Missing ones use the bundled default. */
    public static Messages read(ConfigReader reader) {
        Map<MessageKey, String> templates = new EnumMap<>(MessageKey.class);
        for (MessageKey key : MessageKey.values()) {
            templates.put(key, reader.string(key.path()));
        }
        return new Messages(templates);
    }

    /** The raw MiniMessage text of a message. */
    public String template(MessageKey key) {
        return templates.get(key);
    }

    /**
     * Builds a message. {@code <prefix>} is always available.
     *
     * @param key          which message
     * @param placeholders values for the {@code <name>} tags in the message, see {@link #text}
     */
    public Component render(MessageKey key, TagResolver... placeholders) {
        TagResolver prefix = Placeholder.parsed("prefix", templates.get(MessageKey.PREFIX));
        return MINI_MESSAGE.deserialize(templates.get(key), TagResolver.resolver(prefix, TagResolver.resolver(placeholders)));
    }

    /**
     * A placeholder whose value is shown as plain text. Use this for anything that comes from
     * players or databases (names, ban reasons) so it cannot inject colors or click events.
     */
    public static TagResolver text(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** A placeholder that is itself MiniMessage text. Only use with trusted, server-owned values. */
    public static TagResolver miniMessage(String name, String value) {
        return Placeholder.parsed(name, value);
    }

    /** Parses trusted MiniMessage text from a config file (for example the MOTD). */
    public static Component parse(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage);
    }
}
