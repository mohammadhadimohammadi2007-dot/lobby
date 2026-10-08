package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Renders texts from messages.yml and config files with {@code %placeholders%} filled in.
 * Use this for everything players see.
 */
public final class LobbyText {

    private final ConfigManager config;
    private final PlaceholderService placeholders;

    public LobbyText(ConfigManager config, PlaceholderService placeholders) {
        this.config = config;
        this.placeholders = placeholders;
    }

    /** The placeholder engine. */
    public PlaceholderService placeholders() {
        return placeholders;
    }

    /**
     * A message from messages.yml.
     *
     * @param key       which message
     * @param player    the player the message is about and for, or {@code null} (console, kick before join)
     * @param resolvers values for {@code <name>} tags; use {@code Messages.text(...)} for anything players can influence
     */
    public Component message(MessageKey key, @Nullable Player player, TagResolver... resolvers) {
        var messages = config.current().messages();
        Component prefix = placeholders.render(messages.template(MessageKey.PREFIX), player);
        TagResolver all = TagResolver.resolver(Placeholder.component("prefix", prefix), TagResolver.resolver(resolvers));
        return placeholders.render(messages.template(key), player, all);
    }

    /** A message that is not about a player (for example a kick before the player has joined). */
    public Component message(MessageKey key, TagResolver... resolvers) {
        return message(key, (Player) null, resolvers);
    }

    /** A message for whoever ran a command: placeholders are about them if they are a player. */
    public Component message(MessageKey key, CommandSender sender, TagResolver... resolvers) {
        return message(key, sender instanceof Player player ? player : null, resolvers);
    }

    /** Any trusted MiniMessage text (for example the MOTD) with placeholders about {@code player}. */
    public Component render(String template, @Nullable Player player, TagResolver... resolvers) {
        return placeholders.render(template, player, resolvers);
    }
}
