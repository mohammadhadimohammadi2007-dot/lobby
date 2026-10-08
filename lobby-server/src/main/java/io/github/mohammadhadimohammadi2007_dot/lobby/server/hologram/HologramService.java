package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.YamlDataStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectClicks;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderScope;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The holograms of this server: loads {@code data/holograms.yml}, shows them through the display
 * renderer, saves every change, and runs the actions of a clicked hologram.
 *
 * <p>Names are case-insensitive and stored in lower case, so {@code /hologram delete Welcome} finds
 * {@code welcome}.
 */
public final class HologramService implements ClientObjectClicks.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HologramService.class);
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final String FILE_NAME = "holograms.yml";
    private static final String HEADER = """
            Holograms, normally edited in game with /hologram.

            One entry per hologram. The name is the key. Every option has a default, so a hologram only
            needs a type, a position and (for text) its lines. See docs/holograms.md.""";

    private final YamlDataStore<HologramData> store;
    private final ClientObjectRenderer renderer;
    private final Hologram.Services services;
    private final ActionServices actions;
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();

    private HologramService(YamlDataStore<HologramData> store, ClientObjectRenderer renderer,
                            Hologram.Services services, ActionServices actions) {
        this.store = store;
        this.renderer = renderer;
        this.services = services;
        this.actions = actions;
    }

    /**
     * Loads the file and shows every hologram. Blocking (it reads a file), so call it at startup.
     *
     * @param dataDir the lobby's data folder; the file is {@code <dataDir>/data/holograms.yml}
     */
    public static HologramService start(Path dataDir, ClientObjectRenderer renderer, Hologram.Services services,
                                        ActionServices actions) {
        YamlDataStore<HologramData> store = new YamlDataStore<>(
                dataDir.resolve("data").resolve(FILE_NAME), HEADER, new HologramCodec());
        HologramService service = new HologramService(store, renderer, services, actions);
        service.loadAll();
        return service;
    }

    /** Reads the file again and replaces every hologram. Used by {@code /lobby reload}. */
    public synchronized void reload() {
        for (String name : holograms.keySet()) {
            renderer.remove(name);
        }
        holograms.clear();
        loadAll();
    }

    private synchronized void loadAll() {
        for (HologramData data : store.load().values()) {
            classify(data);
            show(new Hologram(data, services));
        }
        LOGGER.info("Holograms: {} loaded from {}{}", holograms.size(), FILE_NAME,
                store.brokenEntries().isEmpty() ? "" : " (" + store.brokenEntries().size() + " broken, see above)");
    }

    /** How many holograms there are. */
    public synchronized int count() {
        return holograms.size();
    }

    /** Every hologram, in file order. */
    public synchronized List<HologramData> all() {
        return holograms.values().stream().map(Hologram::data).toList();
    }

    /** The hologram with that name, or {@code null}. */
    public synchronized @Nullable HologramData get(String name) {
        Hologram hologram = holograms.get(name.toLowerCase(Locale.ROOT));
        return hologram == null ? null : hologram.data();
    }

    /** The holograms within {@code radius} blocks of a point, nearest first. */
    public synchronized List<HologramData> near(Point point, double radius) {
        double maxSquared = radius * radius;
        return holograms.values().stream()
                .map(Hologram::data)
                .filter(data -> data.position().distanceSquared(point) <= maxSquared)
                .sorted(Comparator.comparingDouble(data -> data.position().distanceSquared(point)))
                .toList();
    }

    /** True if the name may be used: lower-case letters, digits, {@code -} and {@code _}. */
    public static boolean validName(String name) {
        return NAME.matcher(name.toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * Creates a hologram and saves it.
     *
     * @return the new hologram, or {@code null} if the name is taken
     */
    public synchronized @Nullable HologramData create(String name, HologramType type, Pos position,
                                                      List<String> lines) {
        String key = name.toLowerCase(Locale.ROOT);
        if (holograms.containsKey(key)) {
            return null;
        }
        HologramData data = new HologramData(key, type, position, lines);
        classify(data);
        show(new Hologram(data, services));
        save();
        return data;
    }

    /** Copies a hologram under a new name and saves it. */
    public synchronized @Nullable HologramData copy(HologramData source, String newName) {
        String key = newName.toLowerCase(Locale.ROOT);
        if (holograms.containsKey(key)) {
            return null;
        }
        HologramData copy = source.copy(key);
        classify(copy);
        show(new Hologram(copy, services));
        save();
        return copy;
    }

    /** Removes a hologram, despawns it for everyone and saves. True if it existed. */
    public synchronized boolean delete(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (holograms.remove(key) == null) {
            return false;
        }
        renderer.remove(key);
        save();
        return true;
    }

    /** Call after changing a {@link HologramData}: saves the file and shows the change. */
    public synchronized void changed(HologramData data) {
        classify(data);
        renderer.invalidate(data.name());
        save();
    }

    /**
     * Works out how much this hologram's text can differ between players, which decides whether it is
     * rendered once for everybody or once per viewer. Called whenever the lines change.
     *
     * <p>A line written in Persian or Arabic counts as per-player whatever its placeholders say,
     * because each player can turn the right-to-left fix on and off for themselves
     * ({@code /chat persian}), so their lines really are different.
     */
    private void classify(HologramData data) {
        PlaceholderScope scope = PlaceholderScope.STATIC;
        for (List<String> frame : data.frames()) {
            scope = scope.and(services.text().placeholders().scopeOf(frame));
            if (frame.stream().anyMatch(HologramService::needsReshaping)) {
                scope = scope.and(PlaceholderScope.PER_PLAYER);
            }
        }
        data.textScope(scope);
    }

    /** True if the line contains Arabic-script letters, which the Persian fix may reorder per player. */
    private static boolean needsReshaping(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (Character.UnicodeBlock.of(line.charAt(i)) == Character.UnicodeBlock.ARABIC) {
                return true;
            }
        }
        return false;
    }

    /** Parses action lines and puts them on a hologram, keeping the lines as written. */
    public synchronized void setActions(HologramData data, List<String> lines, long cooldownMillis) {
        ActionList parsed = ActionParser.parseList(lines, cooldownMillis,
                FILE_NAME + ": " + data.name() + ".actions", warning -> LOGGER.warn("{}", warning));
        data.actions(lines, parsed);
        changed(data);
    }

    /** Runs the actions of a clicked hologram. */
    @Override
    public boolean clicked(Player player, String objectName, ClientObjectClicks.ClickType type) {
        HologramData data;
        synchronized (this) {
            Hologram hologram = holograms.get(objectName);
            data = hologram == null ? null : hologram.data();
        }
        if (data == null) {
            return false;
        }
        // Both buttons run the same actions, like FancyHolograms: a hologram has one click action.
        data.actions().run(player, actions, "hologram '" + data.name() + "'");
        return true;
    }

    /** Writes every hologram to disk (in the background). */
    public synchronized void save() {
        Map<String, HologramData> entries = new LinkedHashMap<>();
        holograms.forEach((name, hologram) -> entries.put(name, hologram.data()));
        store.save(entries);
    }

    /** Waits until everything is saved and stops the writer. */
    public void shutdown() {
        store.close();
    }

    /** The data file, for tests and messages. */
    public Path file() {
        return store.file();
    }

    private void show(Hologram hologram) {
        holograms.put(hologram.name(), hologram);
        renderer.put(hologram);
    }

    /** Names of entries the file could not read, which are kept untouched. */
    public List<String> brokenEntries() {
        return new ArrayList<>(store.brokenEntries());
    }
}
