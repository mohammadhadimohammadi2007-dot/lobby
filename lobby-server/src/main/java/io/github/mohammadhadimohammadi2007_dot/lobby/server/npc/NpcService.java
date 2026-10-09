package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.YamlDataStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectClicks;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Hologram;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.NameTags;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The NPCs of this server: loads {@code data/npcs.yml}, shows them through the display renderer, looks
 * up their skins, saves every change, and runs the actions of a clicked NPC.
 *
 * <p>Names are case-insensitive and stored in lower case, like holograms.
 */
public final class NpcService implements ClientObjectClicks.Handler, NameTags {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcService.class);
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final String FILE_NAME = "npcs.yml";
    private static final String HEADER = """
            NPCs, normally edited in game with /npc.

            One entry per NPC. The name is the key. "name:" is the hologram above the NPC and takes
            everything a hologram takes. See docs/npcs.md.""";

    private final YamlDataStore<NpcData> store;
    private final ClientObjectRenderer renderer;
    private final Hologram.Services services;
    private final ActionServices actions;
    private final NpcSkins skins;
    private final TeamManager teams;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    /** The team entry each NPC was last put in, so a changed type leaves its old entry behind cleanly. */
    private final Map<String, String> teamEntries = new HashMap<>();

    private NpcService(YamlDataStore<NpcData> store, ClientObjectRenderer renderer, Hologram.Services services,
                       ActionServices actions, NpcSkins skins, TeamManager teams) {
        this.store = store;
        this.renderer = renderer;
        this.services = services;
        this.actions = actions;
        this.skins = skins;
        this.teams = teams;
    }

    /**
     * Loads the file and shows every NPC. Blocking (it reads a file), so call it at startup. Skins that
     * were never looked up are looked up in the background.
     */
    public static NpcService start(Path dataDir, ClientObjectRenderer renderer, Hologram.Services services,
                                   ActionServices actions, NpcSkins skins, TeamManager teams) {
        YamlDataStore<NpcData> store = new YamlDataStore<>(dataDir.resolve("data").resolve(FILE_NAME), HEADER,
                new NpcCodec());
        NpcService service = new NpcService(store, renderer, services, actions, skins, teams);
        service.loadAll();
        return service;
    }

    /** Reads the file again and replaces every NPC. Used by {@code /lobby reload}. */
    public synchronized void reload() {
        for (Npc npc : npcs.values()) {
            renderer.remove(npc.name());
        }
        teamEntries.values().forEach(teams::showName);
        teamEntries.clear();
        npcs.clear();
        loadAll();
    }

    private synchronized void loadAll() {
        for (NpcData data : store.load().values()) {
            classify(data);
            show(data);
            if (data.skin().fetched() && data.resolvedSkin() == null) {
                fetchSkin(data);
            }
        }
        LOGGER.info("NPCs: {} loaded from {}{}", npcs.size(), FILE_NAME,
                store.brokenEntries().isEmpty() ? "" : " (" + store.brokenEntries().size() + " broken, see above)");
    }

    public synchronized int count() {
        return npcs.size();
    }

    /** Every NPC, in file order. */
    public synchronized List<NpcData> all() {
        return npcs.values().stream().map(Npc::data).toList();
    }

    /** The NPC with that name, or {@code null}. */
    public synchronized @Nullable NpcData get(String name) {
        Npc npc = npcs.get(name.toLowerCase(Locale.ROOT));
        return npc == null ? null : npc.data();
    }

    /** The NPCs within {@code radius} blocks, nearest first. */
    public synchronized List<NpcData> near(Point point, double radius) {
        double maxSquared = radius * radius;
        return npcs.values().stream().map(Npc::data)
                .filter(data -> data.position().distanceSquared(point) <= maxSquared)
                .sorted(Comparator.comparingDouble(data -> data.position().distanceSquared(point)))
                .toList();
    }

    /** True if the name may be used: lower-case letters, digits, {@code -} and {@code _}. */
    public static boolean validName(String name) {
        return NAME.matcher(name.toLowerCase(Locale.ROOT)).matches();
    }

    /** Creates a player NPC with the default skin and its name as the name tag, and saves it. */
    public synchronized @Nullable NpcData create(String name, Pos position) {
        String key = name.toLowerCase(Locale.ROOT);
        if (npcs.containsKey(key)) {
            return null;
        }
        NpcData data = new NpcData(key, position, List.of("<yellow>" + key));
        classify(data);
        show(data);
        save();
        return data;
    }

    /** Copies an NPC under a new name and saves it. */
    public synchronized @Nullable NpcData copy(NpcData source, String newName) {
        String key = newName.toLowerCase(Locale.ROOT);
        if (npcs.containsKey(key)) {
            return null;
        }
        NpcData copy = source.copy(key);
        classify(copy);
        show(copy);
        save();
        return copy;
    }

    /** Removes an NPC, despawns it for everyone and saves. True if it existed. */
    public synchronized boolean delete(String name) {
        Npc npc = npcs.remove(name.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        renderer.remove(npc.name());
        String entry = teamEntries.remove(npc.data().name());
        if (entry != null) {
            teams.showName(entry);
        }
        save();
        return true;
    }

    /** Call after changing an {@link NpcData}: saves the file and shows the change. */
    public synchronized void changed(NpcData data) {
        classify(data);
        joinTeam(data);
        renderer.invalidate(Npc.OBJECT_PREFIX + data.name());
        save();
    }

    /**
     * Changes where the skin comes from and looks it up in the background.
     *
     * @return completes with what the lookup found, or why nothing was found
     */
    public CompletableFuture<NpcSkins.Result> setSkin(NpcData data, NpcSkin skin) {
        data.skin(skin);
        if (!skin.fetched()) {
            if (skin.kind() != NpcSkin.Kind.TEXTURE) {
                data.resolvedSkin(null);
            }
            changed(data);
            return CompletableFuture.completedFuture(new NpcSkins.Result(null, ""));
        }
        return fetchSkin(data);
    }

    private CompletableFuture<NpcSkins.Result> fetchSkin(NpcData data) {
        NpcSkin wanted = data.skin();
        return skins.resolve(wanted).thenApply(result -> {
            // Only if nobody changed the skin again while it was being looked up.
            if (result.found() && wanted.equals(data.skin())) {
                if (result.replacement() != null) {
                    data.skin(result.replacement());
                }
                data.resolvedSkin(result.skin());
                changed(data);
            } else if (!result.found()) {
                LOGGER.warn("NPC '{}': no skin for {}: {}", data.name(), wanted.describe(), result.problem());
            }
            return result;
        });
    }

    /** True if image links can be used as skins (a MineSkin API key is set). */
    public boolean canUploadSkins() {
        return skins.canUpload();
    }

    /** Parses action lines and puts them on one trigger of an NPC, keeping the lines as written. */
    public synchronized void setActions(NpcData data, NpcTrigger trigger, List<?> entries) {
        ActionList parsed = ActionParser.parseList(entries, data.clickCooldownMillis(),
                FILE_NAME + ": " + data.name() + "." + trigger.fileKey(), warning -> LOGGER.warn("{}", warning));
        data.actions(trigger, new NpcData.Actions(List.copyOf(entries), parsed));
        changed(data);
    }

    /** Changes the click cooldown of every list of an NPC. */
    public synchronized void setClickCooldown(NpcData data, long millis) {
        data.clickCooldownMillis(millis);
        for (Map.Entry<NpcTrigger, NpcData.Actions> entry : data.allActions().entrySet()) {
            data.actions(entry.getKey(), new NpcData.Actions(entry.getValue().entries(),
                    entry.getValue().parsed().withCooldown(data.clickCooldownMillis())));
        }
        changed(data);
    }

    /** Runs the actions of a clicked NPC; a click on its name tag counts too. */
    @Override
    public boolean clicked(Player player, String objectName, ClientObjectClicks.ClickType type) {
        if (!objectName.startsWith(Npc.OBJECT_PREFIX)) {
            return false;
        }
        NpcData data;
        synchronized (this) {
            Npc npc = npcs.get(objectName.substring(Npc.OBJECT_PREFIX.length()));
            data = npc == null ? null : npc.data();
        }
        if (data == null) {
            return false;
        }
        // "any click" runs on both buttons, next to the list of the button that was used.
        for (Map.Entry<NpcTrigger, NpcData.Actions> entry : data.allActions().entrySet()) {
            if (entry.getKey().runsOn(type)) {
                entry.getValue().parsed().run(player, actions,
                        "NPC '" + data.name() + "' (" + entry.getKey().commandName() + ")");
            }
        }
        return true;
    }

    @Override
    public synchronized @Nullable HologramData nameTagOf(String npcName) {
        NpcData data = get(npcName);
        return data == null ? null : data.nameTag();
    }

    @Override
    public void changed(String npcName) {
        NpcData data = get(npcName);
        if (data != null) {
            changed(data);
        }
    }

    /** Writes every NPC to disk (in the background). */
    public synchronized void save() {
        Map<String, NpcData> entries = new LinkedHashMap<>();
        npcs.forEach((name, npc) -> entries.put(name, npc.data()));
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

    /** Names of entries the file could not read, which are kept untouched. */
    public List<String> brokenEntries() {
        return new ArrayList<>(store.brokenEntries());
    }

    /** See {@link HologramText#classify}: the name tag follows the same rules as any hologram. */
    private void classify(NpcData data) {
        HologramText.classify(data.nameTag(), services.text().placeholders());
    }

    private void show(NpcData data) {
        Npc npc = new Npc(data, services);
        npcs.put(data.name(), npc);
        joinTeam(data);
        renderer.put(npc);
    }

    /**
     * Puts the NPC in its hidden-name team: the real name is the name tag, so the player-list name above
     * the head stays hidden, and the team's colour is the colour the NPC glows in.
     */
    private void joinTeam(NpcData data) {
        String entry = data.teamEntry();
        String previous = teamEntries.put(data.name(), entry);
        if (previous != null && !previous.equals(entry)) {
            teams.showName(previous);
        }
        teams.hideName(entry, data.glowColor());
    }
}
