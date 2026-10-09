package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.FormattedText;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * The {@code scoreboard:} section of {@code display.yml}: boards chosen by permission and priority.
 *
 * @param boards highest priority first
 */
public record SidebarConfig(boolean enabled, List<Board> boards) {

    /** Minecraft shows at most 15 lines in the sidebar. */
    public static final int MAX_LINES = 15;
    public static final int MIN_INTERVAL_TICKS = 2;
    private static final int MAX_INTERVAL_TICKS = 20 * 60 * 10;
    private static final int DEFAULT_INTERVAL_TICKS = 20;

    public SidebarConfig {
        boards = boards.stream().sorted(Comparator.comparingInt(Board::priority).reversed()).toList();
    }

    /**
     * One line and how often it is refreshed.
     *
     * @param text          the line; empty for a spacer
     * @param intervalTicks how often its placeholders are filled in again
     */
    public record Line(TieredText text, int intervalTicks) {
    }

    /**
     * One board.
     *
     * @param title              the title frames; more than one animates it
     * @param titleIntervalTicks how long each title frame shows
     * @param lines              the lines for most clients
     * @param legacyLines        the lines for 1.8-1.12, or {@code null} to use {@link #lines} shortened
     */
    public record Board(String name, int priority, String permission, List<TieredText> title,
                        int titleIntervalTicks, List<Line> lines, @Nullable List<Line> legacyLines) {

        public Board {
            title = List.copyOf(title);
            lines = List.copyOf(lines);
            legacyLines = legacyLines == null ? null : List.copyOf(legacyLines);
        }

        /** The lines for one kind of client. */
        public List<Line> lines(boolean legacyClient) {
            return legacyClient && legacyLines != null ? legacyLines : lines;
        }
    }

    /** The board a player sees: the highest priority one they have the permission for, or {@code null}. */
    public @Nullable Board boardFor(Player player, BiPredicate<Player, String> hasPermission) {
        for (Board board : boards) {
            if (board.permission().isEmpty() || hasPermission.test(player, board.permission())) {
                return board;
            }
        }
        return null;
    }

    static SidebarConfig read(ConfigReader reader) {
        List<Board> boards = new ArrayList<>();
        for (String name : reader.keys("scoreboard.boards")) {
            String path = "scoreboard.boards." + name;
            int interval = reader.integer(path + ".update-interval", MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS,
                    DEFAULT_INTERVAL_TICKS);
            List<String> titles = reader.stringList(path + ".title", List.of(name));
            List<String> legacyTitles = reader.stringList(path + ".legacy-title", List.of());
            List<TieredText> title = new ArrayList<>();
            for (int i = 0; i < Math.max(1, titles.size()); i++) {
                String modern = titles.isEmpty() ? name : titles.get(i);
                String legacy = legacyTitles.isEmpty() ? null : legacyTitles.get(Math.min(i, legacyTitles.size() - 1));
                title.add(TieredText.of(modern, legacy));
            }
            List<Line> lines = lines(reader, path + ".lines", interval);
            List<Line> legacyLines = reader.lineList(path + ".legacy-lines").isEmpty() ? null
                    : lines(reader, path + ".legacy-lines", interval);
            boards.add(new Board(name.toLowerCase(Locale.ROOT),
                    reader.integer(path + ".priority", -1000, 1000, 0),
                    reader.string(path + ".permission", "").strip(), title,
                    reader.integer(path + ".title-interval", MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS, DEFAULT_INTERVAL_TICKS),
                    lines, legacyLines));
        }
        return new SidebarConfig(reader.bool("scoreboard.enabled", true), boards);
    }

    /** Lines are text, or sections with {@code text}, optional {@code legacy} and {@code interval}. */
    private static List<Line> lines(ConfigReader reader, String path, int defaultInterval) {
        List<Line> lines = new ArrayList<>();
        for (Object entry : reader.lineList(path)) {
            if (lines.size() == MAX_LINES) {
                reader.addWarning(reader.fileName() + ": '" + path + "' has more than " + MAX_LINES
                        + " lines; Minecraft only shows " + MAX_LINES + ", the rest are left out.");
                break;
            }
            if (entry instanceof Map<?, ?> section) {
                Object text = section.get("text");
                Object legacy = section.get("legacy");
                int interval = section.get("interval") instanceof Number number
                        ? Math.clamp(number.intValue(), MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS) : defaultInterval;
                lines.add(new Line(TieredText.of(text == null ? "" : String.valueOf(text),
                        legacy == null ? null : String.valueOf(legacy)), interval));
            } else {
                lines.add(new Line(new TieredText(FormattedText.legacyToMiniMessage(String.valueOf(entry)), null),
                        defaultInterval));
            }
        }
        return lines;
    }
}
