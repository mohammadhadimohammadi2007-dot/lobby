package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;
import net.kyori.adventure.bossbar.BossBar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code bossbar:}, {@code action-bar:} and {@code join-title:} sections of {@code display.yml}.
 */
public record BarsConfig(Rotation bossBar, Rotation actionBar, JoinTitle joinTitle) {

    private static final int MIN_ROTATION_TICKS = 20;
    private static final int MAX_TICKS = 20 * 60 * 10;
    private static final int MAX_TITLE_TICKS = 20 * 60;

    /**
     * Messages that take turns.
     *
     * @param intervalTicks how long each message shows before the next one
     * @param messages      every message; each player only gets the ones they have the permission for
     */
    public record Rotation(boolean enabled, int intervalTicks, List<Message> messages) {

        public Rotation {
            messages = List.copyOf(messages);
        }
    }

    /**
     * One message of a rotation; color, style and progress are only used by the boss bar.
     *
     * @param permission needed to get this message, {@code ""} for everyone
     */
    public record Message(TieredText text, String permission, BossBar.Color color, BossBar.Overlay overlay,
                          float progress) {
    }

    /** The title shown once after joining. Times are in ticks. */
    public record JoinTitle(boolean enabled, TieredText title, TieredText subtitle, int fadeIn, int stay, int fadeOut,
                            int delay) {
    }

    static BarsConfig read(ConfigReader reader) {
        JoinTitle joinTitle = new JoinTitle(reader.bool("join-title.enabled", true),
                TieredText.of(reader.string("join-title.title", ""), optional(reader, "join-title.legacy-title")),
                TieredText.of(reader.string("join-title.subtitle", ""), optional(reader, "join-title.legacy-subtitle")),
                reader.integer("join-title.fade-in", 0, MAX_TITLE_TICKS, 10),
                reader.integer("join-title.stay", 0, MAX_TITLE_TICKS, 60),
                reader.integer("join-title.fade-out", 0, MAX_TITLE_TICKS, 20),
                reader.integer("join-title.delay", 0, MAX_TITLE_TICKS, 20));
        return new BarsConfig(rotation(reader, "bossbar"), rotation(reader, "action-bar"), joinTitle);
    }

    private static Rotation rotation(ConfigReader reader, String path) {
        List<Message> messages = new ArrayList<>();
        int index = 0;
        for (Object entry : reader.lineList(path + ".messages")) {
            String where = path + ".messages[" + index++ + "]";
            if (!(entry instanceof Map<?, ?> section) || section.get("text") == null) {
                // A plain line is a message with every other setting at its default.
                messages.add(new Message(TieredText.of(String.valueOf(entry), null), "", BossBar.Color.PINK,
                        BossBar.Overlay.PROGRESS, 1f));
                continue;
            }
            Object legacy = section.get("legacy");
            messages.add(new Message(TieredText.of(String.valueOf(section.get("text")),
                    legacy == null ? null : String.valueOf(legacy)),
                    section.get("permission") == null ? "" : String.valueOf(section.get("permission")).strip(),
                    color(reader, where, section.get("color")),
                    overlay(reader, where, section.get("style")),
                    progress(section.get("progress"))));
        }
        return new Rotation(reader.bool(path + ".enabled", false),
                reader.integer(path + ".interval", MIN_ROTATION_TICKS, MAX_TICKS, 200), messages);
    }

    private static BossBar.Color color(ConfigReader reader, String where, Object value) {
        if (value == null) {
            return BossBar.Color.PINK;
        }
        try {
            return BossBar.Color.valueOf(String.valueOf(value).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            reader.reportInvalid(where + ".color", value, "pink, blue, red, green, yellow, purple or white");
            return BossBar.Color.PINK;
        }
    }

    private static BossBar.Overlay overlay(ConfigReader reader, String where, Object value) {
        if (value == null) {
            return BossBar.Overlay.PROGRESS;
        }
        try {
            return BossBar.Overlay.valueOf(String.valueOf(value).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            reader.reportInvalid(where + ".style", value, "progress, notched_6, notched_10, notched_12 or notched_20");
            return BossBar.Overlay.PROGRESS;
        }
    }

    private static float progress(Object value) {
        return value instanceof Number number ? Math.clamp(number.floatValue(), 0f, 1f) : 1f;
    }

    private static String optional(ConfigReader reader, String path) {
        String value = reader.string(path, "");
        return value.isEmpty() ? null : value;
    }
}
