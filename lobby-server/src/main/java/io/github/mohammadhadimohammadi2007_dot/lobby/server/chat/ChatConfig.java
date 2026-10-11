package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter.ChatFilter;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Everything in {@code chat.yml}, already validated. See that file and {@code docs/chat.md} for what each
 * option does.
 */
public record ChatConfig(
        boolean enabled,
        int maxLength,
        Set<String> playerFormatting,
        Set<String> mutedBlockedCommands,
        boolean instanceOnly,
        List<Format> formats,
        AntiSpam antiSpam,
        Filter filter,
        Mentions mentions,
        Emojis emojis,
        boolean ignoreEnabled,
        boolean chatToggleEnabled,
        int historySize,
        boolean joinMessage,
        boolean quitMessage,
        List<JoinAnnouncement> joinAnnouncements,
        Broadcasts broadcasts,
        Storage storage
) {

    /** Styling tags players may be allowed to use; nothing that can click, hover, change fonts or insert text. */
    public static final Set<String> SAFE_PLAYER_TAGS =
            Set.of("color", "bold", "italic", "underlined", "strikethrough", "obfuscated", "gradient", "rainbow");

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");
    private static final int MIN_MESSAGE_LENGTH = 16;
    private static final int MAX_MESSAGE_LENGTH = 256;

    /** One chat format. {@code legacy} is empty when it should be made from {@code format}. */
    public record Format(String name, int priority, Set<String> groups, String format, String legacy,
                         String messageColor, List<String> hover, String click) {
    }

    /** {@code anti-spam:} section. */
    public record AntiSpam(int cooldownSeconds, int burst, boolean duplicateCheck, int duplicateHistory,
                           int duplicateSimilarityPercent, int maxCapsPercent, int capsMinLength,
                           int maxRepeatedCharacters, int newPlayerDelaySeconds, boolean requireMove) {
    }

    /** What happens when the filter finds something. */
    public enum Action { BLOCK, CENSOR, WARN, NOTIFY }

    /** Actions and points for one kind of filter finding. */
    public record Rule(Set<Action> actions, int points) {
    }

    /** {@code filter:} section. */
    public record Filter(boolean enabled, Map<ChatFilter.Category, Rule> rules, Set<String> allowedDomains,
                         char censorCharacter, AutoMute autoMute) {
    }

    /** {@code filter.auto-mute:} section. */
    public record AutoMute(boolean enabled, int threshold, int decayPerMinute, int minutes, String proxyCommand) {
    }

    /** {@code mentions:} section; {@code sound} is empty for no sound. */
    public record Mentions(boolean enabled, String highlight, String sound, int cooldownSeconds) {
    }

    /** One emoji: {@code :name:} becomes {@code symbol}, or {@code legacy} for old clients. */
    public record Emoji(String symbol, String legacy) {
    }

    /** {@code emojis:} section. */
    public record Emojis(boolean enabled, Map<String, Emoji> list) {
    }

    /** A rank's join announcement. */
    public record JoinAnnouncement(String name, String permission, String message) {
    }

    /** {@code broadcasts:} section. */
    public record Broadcasts(boolean enabled, int intervalMinutes, boolean random, List<String> messages,
                             List<String> legacyMessages) {
    }

    /** {@code storage:} section. */
    public record Storage(String tablePrefix, boolean logToDatabase, int retentionDays, boolean logToFile) {
    }

    /**
     * Reads and validates chat.yml.
     *
     * @throws ConfigException if the table prefix contains characters that are not allowed
     */
    public static ChatConfig read(ConfigReader reader) throws ConfigException {
        String tablePrefix = reader.string("storage.table-prefix").trim();
        if (!SAFE_PREFIX.matcher(tablePrefix).matches()) {
            throw new ConfigException("chat.yml: option 'storage.table-prefix' has an invalid value '" + tablePrefix
                    + "'. Allowed: letters, numbers and _ only (max 32 characters).");
        }
        return new ChatConfig(
                reader.bool("enabled"),
                reader.integer("max-length", MIN_MESSAGE_LENGTH, MAX_MESSAGE_LENGTH),
                readPlayerFormatting(reader),
                lowerCaseSet(reader.stringList("muted-blocked-commands")),
                reader.bool("instance-only"),
                readFormats(reader),
                new AntiSpam(
                        reader.integer("anti-spam.cooldown", 0, 60),
                        reader.integer("anti-spam.burst", 1, 10),
                        reader.bool("anti-spam.duplicate-check"),
                        reader.integer("anti-spam.duplicate-history", 1, 10),
                        reader.integer("anti-spam.duplicate-similarity", 50, 100),
                        reader.integer("anti-spam.max-caps-percent", 0, 100),
                        reader.integer("anti-spam.caps-min-length", 1, 100),
                        reader.integer("anti-spam.max-repeated-characters", 0, 100),
                        reader.integer("anti-spam.new-player-delay", 0, 600),
                        reader.bool("anti-spam.require-move")),
                readFilter(reader),
                new Mentions(reader.bool("mentions.enabled"), reader.string("mentions.highlight"),
                        reader.string("mentions.sound").trim(), reader.integer("mentions.cooldown", 0, 300)),
                readEmojis(reader),
                reader.bool("ignore-enabled"),
                reader.bool("chat-toggle-enabled"),
                reader.integer("history-size", 10, 500),
                reader.bool("join-message"),
                reader.bool("quit-message"),
                readJoinAnnouncements(reader),
                new Broadcasts(reader.bool("broadcasts.enabled"), reader.integer("broadcasts.interval", 1, 1440),
                        reader.bool("broadcasts.random"), reader.stringList("broadcasts.messages"),
                        reader.stringList("broadcasts.legacy-messages")),
                new Storage(tablePrefix, reader.bool("storage.log-to-database"),
                        reader.integer("storage.log-retention-days", 1, 3650), reader.bool("storage.log-to-file")));
    }

    private static Set<String> readPlayerFormatting(ConfigReader reader) {
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : reader.stringList("player-formatting")) {
            String name = tag.toLowerCase(Locale.ROOT);
            if (SAFE_PLAYER_TAGS.contains(name)) {
                tags.add(name);
            } else {
                reader.reportInvalid("player-formatting", tag, "only " + String.join(", ", SAFE_PLAYER_TAGS.stream().sorted().toList()));
            }
        }
        return Set.copyOf(tags);
    }

    private static List<Format> readFormats(ConfigReader reader) {
        List<Format> formats = new ArrayList<>();
        for (String key : reader.keys("formats")) {
            String path = "formats." + key;
            String name = key.toLowerCase(Locale.ROOT);
            if (!NAME.matcher(name).matches()) {
                reader.reportInvalid(path, key, "format names made of a-z, 0-9, - and _");
                continue;
            }
            formats.add(new Format(name,
                    reader.integer(path + ".priority", -1000, 1000, 0),
                    lowerCaseSet(reader.stringList(path + ".groups", List.of())),
                    reader.string(path + ".format", "<name>: <message>"),
                    reader.string(path + ".legacy", ""),
                    reader.string(path + ".message-color", ""),
                    reader.stringList(path + ".hover", List.of()),
                    reader.string(path + ".click", "").strip()));
        }
        formats.sort(Comparator.comparingInt(Format::priority).reversed());
        return List.copyOf(formats);
    }

    private static Filter readFilter(ConfigReader reader) {
        Map<ChatFilter.Category, Rule> rules = new EnumMap<>(ChatFilter.Category.class);
        for (ChatFilter.Category category : ChatFilter.Category.values()) {
            String path = "filter." + category.configName();
            Set<Action> actions = EnumSet.noneOf(Action.class);
            for (String action : reader.stringList(path + ".actions")) {
                try {
                    actions.add(Action.valueOf(action.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    reader.reportInvalid(path + ".actions", action, "block, censor, warn, notify");
                }
            }
            rules.put(category, new Rule(actions, reader.integer(path + ".points", 0, 1000)));
        }
        String censor = reader.string("filter.censor-character");
        char censorCharacter = censor.isEmpty() ? '*' : censor.charAt(0);
        return new Filter(
                reader.bool("filter.enabled"),
                rules,
                lowerCaseSet(reader.stringList("filter.allowed-domains")),
                censorCharacter,
                new AutoMute(reader.bool("filter.auto-mute.enabled"),
                        reader.integer("filter.auto-mute.threshold", 1, 1000),
                        reader.integer("filter.auto-mute.decay-per-minute", 0, 100),
                        reader.integer("filter.auto-mute.minutes", 1, 10080),
                        reader.string("filter.auto-mute.proxy-command").strip()));
    }

    private static Emojis readEmojis(ConfigReader reader) {
        Map<String, Emoji> list = new LinkedHashMap<>();
        for (String key : reader.keys("emojis.list")) {
            String path = "emojis.list." + key;
            String symbol = reader.string(path + ".symbol", "");
            if (symbol.isEmpty()) {
                reader.reportInvalid(path + ".symbol", symbol, "a symbol");
                continue;
            }
            list.put(key.toLowerCase(Locale.ROOT), new Emoji(symbol, reader.string(path + ".legacy", symbol)));
        }
        return new Emojis(reader.bool("emojis.enabled"), list);
    }

    private static List<JoinAnnouncement> readJoinAnnouncements(ConfigReader reader) {
        List<JoinAnnouncement> list = new ArrayList<>();
        for (String key : reader.keys("join-announcements")) {
            String path = "join-announcements." + key;
            String permission = reader.string(path + ".permission", "").strip();
            String message = reader.string(path + ".message", "");
            if (permission.isEmpty() || message.isEmpty()) {
                reader.reportInvalid(path, key, "an entry with a permission and a message");
                continue;
            }
            list.add(new JoinAnnouncement(key, permission, message));
        }
        return List.copyOf(list);
    }

    private static Set<String> lowerCaseSet(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.strip().toLowerCase(Locale.ROOT)));
        return Set.copyOf(result);
    }
}
