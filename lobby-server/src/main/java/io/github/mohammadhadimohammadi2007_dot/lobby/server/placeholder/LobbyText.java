package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Renders texts from messages.yml and config files with {@code %placeholders%} filled in.
 * Use this for everything players see.
 */
public final class LobbyText {

    private final ConfigManager config;
    private final PlaceholderService placeholders;
    private volatile BiFunction<Player, Component, Component> viewerTransform = (player, component) -> component;
    private volatile Predicate<Player> viewerTransformApplies = player -> false;

    public LobbyText(ConfigManager config, PlaceholderService placeholders) {
        this.config = config;
        this.placeholders = placeholders;
    }

    /**
     * Changes every text after rendering for one player, for example fixing Persian for players who want it
     * (set by the chat system when persian.server-messages is on).
     *
     * @param transform what to do with a rendered text for one viewer
     * @param applies   true for the viewers it really changes something for. The display layer groups
     *                  viewers by this answer, so a hologram is rendered once per group instead of once
     *                  per player
     */
    public void viewerTransform(BiFunction<Player, Component, Component> transform, Predicate<Player> applies) {
        viewerTransform = transform;
        viewerTransformApplies = applies;
    }

    /**
     * True if rendered texts are changed for this viewer. Two viewers with the same answer get exactly
     * the same text, so they can share one render.
     */
    public boolean transformsFor(Player viewer) {
        return viewerTransformApplies.test(viewer);
    }

    /**
     * Applies the viewer's text fix to an already rendered text, for text that is about someone else
     * (a name in the tab list) but read by {@code viewer}.
     */
    public Component forViewer(Player viewer, Component rendered) {
        return viewerTransform.apply(viewer, rendered);
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
        Component rendered = placeholders.render(messages.template(key), player, all);
        return player == null ? rendered : viewerTransform.apply(player, rendered);
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
        Component rendered = placeholders.render(template, player, resolvers);
        return player == null ? rendered : viewerTransform.apply(player, rendered);
    }
}
