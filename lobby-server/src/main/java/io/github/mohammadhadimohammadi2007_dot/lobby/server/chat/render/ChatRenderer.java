package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.FormattedText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Builds the components players see for a chat message: the rank format around the player's name (with hover and click) and message (with color, emojis and mention highlight).
 */
public final class ChatRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final LobbyText text;
    private final Supplier<ChatConfig> config;
    private final Supplier<Messages> messages;
    private volatile PlayerTextParser parser;
    private volatile ChatConfig parserConfig;

    public ChatRenderer(LobbyText text, Supplier<ChatConfig> config, Supplier<Messages> messages) {
        this.text = text;
        this.config = config;
        this.messages = messages;
    }

    /**
     * Prepares a message for delivery; versions are rendered on demand.
     *
     * @param styling true if the sender may style their message (lobby.chat.formatting)
     * @param emojis  true if the sender may use emojis (lobby.chat.emoji)
     */
    public RenderedMessage prepare(ChatMessage message, boolean styling, boolean emojis) {
        ChatConfig chat = config.get();
        PlayerMeta meta = message.senderMeta();
        Component prefix = FormattedText.parse(meta.prefix());
        Component suffix = FormattedText.parse(meta.suffix());
        return new RenderedMessage(key -> render(message, chat, key, styling, emojis, prefix, suffix));
    }

    private Component render(ChatMessage message, ChatConfig chat, VariantKey key, boolean styling, boolean emojis,
                             Component prefix, Component suffix) {
        ChatConfig.Format format = message.format();
        Component body = body(message, chat, key, styling, emojis);
        Component coloredBody = format.messageColor().isBlank()
                ? body
                : MINI_MESSAGE.deserialize(format.messageColor() + "<lobby_message_body>",
                Placeholder.component("lobby_message_body", body));

        TagResolver tags = TagResolver.resolver(
                Placeholder.component("name", nameComponent(message, format, key)),
                Placeholder.component("message", coloredBody),
                Placeholder.component("prefix", prefix),
                Placeholder.component("suffix", suffix));

        boolean ownLegacy = key.legacy() && !format.legacy().isBlank();
        Component full = renderTemplate(message, ownLegacy ? format.legacy() : format.format(), tags);
        return key.legacy() && !ownLegacy ? ComponentTransforms.downsampleColors(full) : full;
    }

    /** The message text with emojis, styling and the viewer's name highlighted. */
    private Component body(ChatMessage message, ChatConfig chat, VariantKey key, boolean styling, boolean emojis) {
        String raw = emojis && chat.emojis().enabled()
                ? EmojiReplacer.apply(message.text(), chat.emojis().list(), key.legacy())
                : message.text();
        Component body;
        if (styling && (raw.indexOf('<') >= 0 || raw.indexOf('&') >= 0)) {
            body = parser(chat).parse(raw, true);
        } else {
            body = Component.text(raw);
        }
        if (key.mentioned() != null) {
            String name = message.mentionedNames().get(key.mentioned());
            if (name != null) {
                Component highlighted = MINI_MESSAGE.deserialize(chat.mentions().highlight() + "<lobby_name>",
                        Placeholder.unparsed("lobby_name", name));
                body = body.replaceText(TextReplacementConfig.builder()
                        .match(Pattern.compile("@?" + Pattern.quote(name), Pattern.CASE_INSENSITIVE))
                        .replacement(highlighted)
                        .build());
            }
        }
        return body;
    }

    /** The sender's name with hover lines and click action from the format. */
    private Component nameComponent(ChatMessage message, ChatConfig.Format format, VariantKey key) {
        Component name = Component.text(message.senderName());
        List<Component> lines = new ArrayList<>();
        for (String line : format.hover()) {
            if (!line.isBlank()) {
                lines.add(renderTemplate(message, line, TagResolver.empty()));
            }
        }
        if (key.staff()) {
            lines.add(messages.get().render(MessageKey.CHAT_STAFF_HOVER, Messages.text("id", message.id())));
        }
        if (!lines.isEmpty()) {
            name = name.hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), lines)));
        }
        ClickEvent click = clickEvent(format.click(), message.senderName());
        return click == null ? name : name.clickEvent(click);
    }

    /** {@code suggest_command:/msg <name> } and friends; {@code null} if empty or unknown. */
    static @Nullable ClickEvent clickEvent(String spec, String senderName) {
        int colon = spec.indexOf(':');
        if (spec.isBlank() || colon < 0) {
            return null;
        }
        String action = spec.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = spec.substring(colon + 1).replace("<name>", senderName);
        return switch (action) {
            case "suggest_command" -> ClickEvent.suggestCommand(value);
            case "run_command" -> ClickEvent.runCommand(value);
            case "open_url" -> ClickEvent.openUrl(value);
            case "copy_to_clipboard" -> ClickEvent.copyToClipboard(value);
            default -> null;
        };
    }

    /** Renders a format with placeholders about the sender. */
    private Component renderTemplate(ChatMessage message, String template, TagResolver tags) {
        return text.placeholders().render(template, message.sender(), (Player) null, tags);
    }

    private PlayerTextParser parser(ChatConfig chat) {
        if (parser == null || parserConfig != chat) {
            parser = new PlayerTextParser(chat.playerFormatting());
            parserConfig = chat;
        }
        return parser;
    }
}
