package io.github.mohammadhadimohammadi2007_dot.lobby.server.team;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.LegacyText;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.color.TeamColor;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.network.packet.server.play.TeamsPacket.CollisionRule;
import net.minestom.server.network.packet.server.play.TeamsPacket.NameTagVisibility;
import net.minestom.server.utils.PacketSendingUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The only place in the lobby that creates scoreboard teams. Minecraft allows each name in only one team,
 * and teams decide tab order, nametag prefix/suffix, hidden NPC names and collisions all at once, so
 * features that made their own teams would silently break each other.
 *
 * <p>Team names keep features apart: {@code <order>p<id>} for players (sorted by order in tab),
 * {@code zzhidden} for NPCs whose names must not show, {@code s<id>} for per-viewer sidebar lines.
 * Clients older than 1.13 only allow 16 characters in a prefix or suffix, so they get shortened copies.
 */
public final class TeamManager {

    /** Prefix and suffix limit of 1.8-1.12 clients, color codes included. */
    public static final int LEGACY_LIMIT = 16;
    /** Highest tab order; lower orders are listed first. */
    public static final int MAX_ORDER = 99;
    private static final String HIDDEN_TEAM = "zzhidden";
    private static final String PRIVATE_PREFIX = "s";
    private static final int MAX_PRIVATE_ID = 15;
    private static final byte NO_FRIENDLY_FLAGS = 0;

    /** One team as all clients know it. */
    private record Team(String name, Component prefix, Component suffix, TeamColor color,
                        NameTagVisibility nameTag, CollisionRule collision, Set<String> members) {

        TeamsPacket.Settings settings(boolean legacy) {
            Component shortPrefix = legacy ? LegacyText.limit(prefix, LEGACY_LIMIT) : prefix;
            Component shortSuffix = legacy ? LegacyText.limit(suffix, LEGACY_LIMIT) : suffix;
            return new TeamsPacket.Settings(Component.empty(), shortPrefix, shortSuffix, nameTag, collision, color,
                    NO_FRIENDLY_FLAGS);
        }

        TeamsPacket create(boolean legacy) {
            return new TeamsPacket(name, new TeamsPacket.CreateTeamAction(settings(legacy), List.copyOf(members)));
        }
    }

    private final ToIntFunction<Player> protocolOf;
    private final CollisionRule playerCollision;
    private final Map<String, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<UUID, String> playerIds = new HashMap<>();
    private int nextId;

    /**
     * @param protocolOf      each viewer's real protocol version (from the bridge)
     * @param playerCollision true to let players push each other
     */
    public TeamManager(ToIntFunction<Player> protocolOf, boolean playerCollision) {
        this.protocolOf = protocolOf;
        this.playerCollision = playerCollision ? CollisionRule.ALWAYS : CollisionRule.NEVER;
        teams.put(HIDDEN_TEAM, new Team(HIDDEN_TEAM, Component.empty(), Component.empty(), TeamColor.WHITE,
                NameTagVisibility.NEVER, CollisionRule.NEVER, new LinkedHashSet<>()));
    }

    /** Sends every team to players when they join, and removes a player's team when they leave. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                sendAllTo(event.getPlayer());
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> removePlayer(event.getPlayer()));
    }

    /**
     * Puts {@code player} in their own team: listed by {@code order} in tab (0 first) with the given nametag
     * prefix, suffix and name color. Only what changed is sent.
     */
    public synchronized void setPlayer(Player player, int order, Component prefix, Component suffix, TeamColor color) {
        String name = String.format("%02d", Math.clamp(order, 0, MAX_ORDER)) + "p" + idOf(player);
        Team wanted = new Team(name, prefix, suffix, color, NameTagVisibility.ALWAYS, playerCollision,
                Set.of(player.getUsername()));
        String current = playerTeams.get(player.getUuid());
        if (current != null && current.equals(name)) {
            Team old = teams.get(name);
            if (old.equals(wanted)) {
                return;
            }
            teams.put(name, wanted);
            broadcast(legacy -> new TeamsPacket(name, new TeamsPacket.UpdateTeamAction(wanted.settings(legacy))));
            return;
        }
        if (current != null) {
            // The order is part of the name (tab sorts by team name), so a new order needs a new team.
            teams.remove(current);
            broadcast(legacy -> new TeamsPacket(current, new TeamsPacket.RemoveTeamAction()));
        }
        teams.put(name, wanted);
        playerTeams.put(player.getUuid(), name);
        broadcast(wanted::create);
    }

    /** Removes the player's team (when they leave). */
    public synchronized void removePlayer(Player player) {
        String current = playerTeams.remove(player.getUuid());
        playerIds.remove(player.getUuid());
        if (current != null) {
            teams.remove(current);
            broadcast(legacy -> new TeamsPacket(current, new TeamsPacket.RemoveTeamAction()));
        }
    }

    /**
     * Hides the name above an entity: a player NPC's profile name, or another entity's UUID as text.
     * Hidden entities also never collide.
     */
    public synchronized void hideName(String entry) {
        Team hidden = teams.get(HIDDEN_TEAM);
        if (hidden.members().add(entry)) {
            broadcast(legacy -> new TeamsPacket(HIDDEN_TEAM, new TeamsPacket.AddEntitiesToTeamAction(List.of(entry))));
        }
    }

    /** Shows the name above an entity again. */
    public synchronized void showName(String entry) {
        Team hidden = teams.get(HIDDEN_TEAM);
        if (hidden.members().remove(entry)) {
            broadcast(legacy -> new TeamsPacket(HIDDEN_TEAM, new TeamsPacket.RemoveEntitiesToTeamAction(List.of(entry))));
        }
    }

    /**
     * Creates or updates a team that only {@code viewer} knows about, holding {@code entry} (for sidebar
     * lines). {@code id} must be unique for this viewer and at most 15 characters.
     */
    public void sendPrivate(Player viewer, String id, boolean create, Component prefix, Component suffix, String entry) {
        String name = privateName(id);
        boolean legacy = isLegacy(viewer);
        Team team = new Team(name, prefix, suffix, TeamColor.WHITE, NameTagVisibility.ALWAYS, CollisionRule.NEVER,
                Set.of(entry));
        viewer.sendPacket(create ? team.create(legacy)
                : new TeamsPacket(name, new TeamsPacket.UpdateTeamAction(team.settings(legacy))));
    }

    /** Removes a team created with {@link #sendPrivate}. */
    public void removePrivate(Player viewer, String id) {
        viewer.sendPacket(new TeamsPacket(privateName(id), new TeamsPacket.RemoveTeamAction()));
    }

    /** True if the viewer's client has the 16-character prefix limit. */
    public boolean isLegacy(Player viewer) {
        return protocolOf.applyAsInt(viewer) < ProtocolVersions.V1_13;
    }

    /** Sends every shared team to one player. */
    public synchronized void sendAllTo(Player viewer) {
        boolean legacy = isLegacy(viewer);
        for (Team team : teams.values()) {
            viewer.sendPacket(team.create(legacy));
        }
    }

    /** Names of all shared teams, for tests and debugging. */
    public synchronized List<String> teamNames() {
        return new ArrayList<>(teams.keySet());
    }

    private String idOf(Player player) {
        return playerIds.computeIfAbsent(player.getUuid(), ignored -> Integer.toString(nextId++, Character.MAX_RADIX));
    }

    private static String privateName(String id) {
        if (id.isEmpty() || id.length() > MAX_PRIVATE_ID) {
            throw new IllegalArgumentException("private team id must be 1-" + MAX_PRIVATE_ID + " characters: " + id);
        }
        return PRIVATE_PREFIX + id;
    }

    /** Sends a packet to every online player, built once for new clients and once for old ones. */
    private void broadcast(Function<Boolean, ServerPacket> packet) {
        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        List<Player> modern = new ArrayList<>(online.size());
        List<Player> legacy = new ArrayList<>();
        for (Player player : online) {
            (isLegacy(player) ? legacy : modern).add(player);
        }
        if (!modern.isEmpty()) {
            PacketSendingUtils.sendGroupedPacket(modern, packet.apply(false));
        }
        if (!legacy.isEmpty()) {
            PacketSendingUtils.sendGroupedPacket(legacy, packet.apply(true));
        }
    }
}
