package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Renders MiniMessage text that contains {@code %placeholders%}.
 *
 * <p><b>Security:</b> placeholder values are never pasted into the MiniMessage text. Each one becomes its own
 * component and is inserted through a tag, so a value like {@code <click:...>} or {@code &c} from a player
 * name is shown exactly as written. Values marked {@link Placeholder#formatted()} (trusted config and
 * LuckPerms data) may contain colors, but still cannot affect the text around them.
 *
 * <p>Thread-safe; caches global and per-player values (see {@link Placeholder}).
 */
public final class PlaceholderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceholderService.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String TAG_PREFIX = "lobby_placeholder_";
    /** Parsed templates kept in memory; configs have far fewer distinct texts than this. */
    private static final int MAX_TEMPLATES = 4_096;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final PlaceholderRegistry registry;
    private final Map<String, PlaceholderTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, Cached> globalValues = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Cached>> playerValues = new ConcurrentHashMap<>();

    public PlaceholderService(PlaceholderRegistry registry) {
        this.registry = registry;
    }

    /** The registry, for adding namespaces. */
    public PlaceholderRegistry registry() {
        return registry;
    }

    /** Forgets cached values when players leave. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerDisconnectEvent.class, event -> invalidate(event.getPlayer().getUuid()));
    }

    /**
     * Renders {@code template} with placeholders about {@code player} (who is also the one reading it).
     *
     * @param template MiniMessage text from a trusted source (config)
     * @param player   the player the text is about and for, or {@code null} (then only global placeholders work)
     * @param extra    additional {@code <name>} tags, e.g. from {@code Messages.text(...)}
     */
    public Component render(String template, @Nullable Player player, TagResolver... extra) {
        return render(template, player, player, extra);
    }

    /**
     * Renders {@code template} where it matters who the text is about and who reads it, as in chat:
     * player placeholders describe {@code target} (the sender), relational ones use both.
     */
    public Component render(String template, @Nullable Player target, @Nullable Player viewer, TagResolver... extra) {
        PlaceholderTemplate parsed = template(template);
        if (!parsed.hasReferences()) {
            return MINI_MESSAGE.deserialize(template, TagResolver.resolver(extra));
        }
        StringBuilder source = new StringBuilder(template.length() + 32);
        List<Component> inserted = new ArrayList<>();
        for (PlaceholderTemplate.Piece piece : parsed.pieces()) {
            switch (piece) {
                case PlaceholderTemplate.Literal literal -> source.append(literal.text());
                case PlaceholderTemplate.Reference reference -> {
                    Cached value = value(reference.key(), target, viewer);
                    if (value == null) {
                        // Unknown, or needs a player we do not have: keep it as written, like PlaceholderAPI.
                        source.append(MINI_MESSAGE.escapeTags('%' + reference.key() + '%'));
                    } else if (!reference.acceptsComponent()) {
                        source.append(sanitizeForTagArgument(value.plain()));
                    } else {
                        source.append('<').append(TAG_PREFIX).append(inserted.size()).append('>');
                        inserted.add(value.component());
                    }
                }
            }
        }
        TagResolver insertedTags = (TagResolver.WithoutArguments) name -> {
            if (!name.startsWith(TAG_PREFIX)) {
                return null;
            }
            String index = name.substring(TAG_PREFIX.length());
            return isIndex(index, inserted.size()) ? Tag.selfClosingInserting(inserted.get(Integer.parseInt(index))) : null;
        };
        return MINI_MESSAGE.deserialize(source.toString(), TagResolver.resolver(insertedTags, TagResolver.resolver(extra)));
    }

    /**
     * The value of one placeholder as a component, or {@code null} if unknown.
     *
     * @param key without the {@code %} signs, e.g. {@code luckperms_prefix}
     */
    public @Nullable Component component(String key, @Nullable Player target, @Nullable Player viewer) {
        Cached value = value(key, target, viewer);
        return value == null ? null : value.component();
    }

    /** The value of one placeholder as plain text without colors, or {@code null} if unknown. */
    public @Nullable String plain(String key, @Nullable Player target, @Nullable Player viewer) {
        Cached value = value(key, target, viewer);
        return value == null ? null : value.plain();
    }

    /** Drops every cached value about one player, e.g. after a rank change. */
    public void invalidate(UUID playerId) {
        playerValues.remove(playerId);
    }

    /** Drops every cached value, e.g. after {@code /lobby reload}. */
    public void invalidateAll() {
        playerValues.clear();
        globalValues.clear();
        templates.clear();
    }

    private PlaceholderTemplate template(String text) {
        PlaceholderTemplate cached = templates.get(text);
        if (cached != null) {
            return cached;
        }
        PlaceholderTemplate parsed = PlaceholderTemplate.parse(text);
        if (templates.size() < MAX_TEMPLATES) {
            templates.put(text, parsed);
        }
        return parsed;
    }

    private @Nullable Cached value(String key, @Nullable Player target, @Nullable Player viewer) {
        Placeholder placeholder = registry.find(key);
        if (placeholder == null) {
            return null;
        }
        try {
            return switch (placeholder) {
                case Placeholder.Global global -> cached(globalValues, key, global.cacheTime().toMillis(),
                        () -> global.value().get(), global.formatted());
                case Placeholder.PerPlayer perPlayer -> {
                    if (target == null) {
                        yield null;
                    }
                    Map<String, Cached> values = playerValues.computeIfAbsent(target.getUuid(), id -> new ConcurrentHashMap<>());
                    long ttl = perPlayer.cacheTime() == null ? -1 : perPlayer.cacheTime().toMillis();
                    yield cached(values, key, ttl, () -> perPlayer.value().apply(target), perPlayer.formatted());
                }
                case Placeholder.Relational relational -> target == null || viewer == null
                        ? null
                        : Cached.of(relational.value().apply(viewer, target), relational.formatted(), Long.MAX_VALUE);
            };
        } catch (RuntimeException e) {
            LOGGER.warn("Placeholder %{}% failed", key, e);
            return null;
        }
    }

    /** Returns a fresh cached value or computes it. {@code ttlMillis} -1 means "until invalidated". */
    private static Cached cached(Map<String, Cached> cache, String key, long ttlMillis,
                                 Supplier<String> compute, boolean formatted) {
        long now = System.nanoTime();
        Cached current = cache.get(key);
        if (current != null && current.expiresAtNanos() > now) {
            return current;
        }
        long expires = ttlMillis < 0 ? Long.MAX_VALUE : now + ttlMillis * NANOS_PER_MILLI;
        Cached fresh = Cached.of(compute.get(), formatted, expires);
        cache.put(key, fresh);
        return fresh;
    }

    /** Removes characters that could end a MiniMessage tag argument early. */
    static String sanitizeForTagArgument(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '<' && c != '>' && c != '\'' && c != '"' && c != '\\' && c != ':') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isIndex(String text, int size) {
        if (text.isEmpty() || text.length() > 6) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return Integer.parseInt(text) < size;
    }

    /** A computed value in both forms, with its expiry time. */
    private record Cached(Component component, String plain, long expiresAtNanos) {
        static Cached of(String value, boolean formatted, long expiresAtNanos) {
            String text = value == null ? "" : value;
            Component component = formatted ? FormattedText.parse(text) : Component.text(text);
            return new Cached(component, formatted ? PLAIN.serialize(component) : text, expiresAtNanos);
        }
    }
}
