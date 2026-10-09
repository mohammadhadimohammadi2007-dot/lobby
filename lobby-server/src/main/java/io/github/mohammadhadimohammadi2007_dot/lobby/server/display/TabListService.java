package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.PlayerListHeaderAndFooterPacket;
import net.minestom.server.utils.PacketSendingUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The tab list: header and footer per player, every player's name with their rank, and, if wanted, only
 * the players of the viewer's own lobby instance.
 *
 * <p>Names are the same for every viewer, so each is rendered once per refresh (once more for 1.8-1.12
 * clients), and only names that changed are sent: all of them in one packet per group of viewers.
 * Only used on the display thread.
 */
final class TabListService {

    /** One rendered name of a player in both client versions. */
    private record Name(Component modern, Component legacy) {
    }

    /** Viewers who get exactly the same names. */
    private record Group(boolean legacy, boolean reshaped) {
    }

    private final ClientTiers tiers;
    private final LobbyText text;
    private final Map<UUID, Name> names = new HashMap<>();
    private final Set<UUID> knowNames = new HashSet<>();
    private final Map<UUID, Component[]> headers = new HashMap<>();
    private final Map<UUID, Set<UUID>> unlisted = new HashMap<>();
    private int namePackets;

    TabListService(ClientTiers tiers, LobbyText text) {
        this.tiers = tiers;
        this.text = text;
    }

    /** Names and who is listed: every tab interval, for everybody at once, so the names go in one packet. */
    void refresh(TabConfig config, Collection<Player> online, TextCache cache) {
        if (!config.enabled()) {
            return;
        }
        names(config, online, cache);
        listing(config.sameInstanceOnly(), online);
    }

    /** Header and footer: on every refresh, for the players whose turn it is ({@link Stagger}). */
    void headerAndFooter(TabConfig config, Collection<Player> online, TextCache cache, long previousTicks, long ticks) {
        if (!config.enabled()) {
            return;
        }
        for (Player viewer : online) {
            if (headers.containsKey(viewer.getUuid())
                    && !Stagger.due(previousTicks, ticks, config.intervalTicks(), viewer.getUuid())) {
                continue;
            }
            Component header = cache.render(config.header(), viewer);
            Component footer = cache.render(config.footer(), viewer);
            Component[] sent = headers.get(viewer.getUuid());
            if (sent == null || !sent[0].equals(header) || !sent[1].equals(footer)) {
                headers.put(viewer.getUuid(), new Component[]{header, footer});
                viewer.sendPacket(new PlayerListHeaderAndFooterPacket(header, footer));
            }
        }
    }

    private void names(TabConfig config, Collection<Player> online, TextCache cache) {
        List<Player> changed = new ArrayList<>();
        for (Player target : online) {
            Name name = new Name(cache.about(config.nameFormat().template(false), target, false),
                    cache.about(config.nameFormat().template(true), target, true));
            if (!name.equals(names.put(target.getUuid(), name))) {
                changed.add(target);
            }
        }
        Map<Group, List<Player>> fresh = new LinkedHashMap<>();
        Map<Group, List<Player>> known = new LinkedHashMap<>();
        for (Player viewer : online) {
            Group group = new Group(tiers.legacyText(viewer), text.transformsFor(viewer));
            (knowNames.add(viewer.getUuid()) ? fresh : known).computeIfAbsent(group, ignored -> new ArrayList<>())
                    .add(viewer);
        }
        // Players who just joined get every name, the others only the ones that changed.
        fresh.forEach((group, viewers) -> sendNames(group, viewers, online));
        if (!changed.isEmpty()) {
            known.forEach((group, viewers) -> sendNames(group, viewers, changed));
        }
    }

    private void sendNames(Group group, List<Player> viewers, Collection<Player> targets) {
        Player representative = viewers.getFirst();
        List<PlayerInfoUpdatePacket.Entry> entries = new ArrayList<>(targets.size());
        for (Player target : targets) {
            Name name = names.get(target.getUuid());
            Component shown = group.legacy() ? name.legacy() : name.modern();
            if (group.reshaped()) {
                shown = text.forViewer(representative, shown);
            }
            entries.add(entry(target, true, shown));
        }
        PlayerInfoUpdatePacket packet = new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                entries);
        PacketSendingUtils.sendGroupedPacket(viewers, packet);
        namePackets++;
    }

    /** Hides the players of other instances from each viewer, or shows everyone again. */
    private void listing(boolean sameInstanceOnly, Collection<Player> online) {
        for (Player viewer : online) {
            Set<UUID> wanted = new HashSet<>();
            if (sameInstanceOnly) {
                Instance own = viewer.getInstance();
                for (Player target : online) {
                    if (target != viewer && !Objects.equals(target.getInstance(), own)) {
                        wanted.add(target.getUuid());
                    }
                }
            }
            Set<UUID> current = unlisted.getOrDefault(viewer.getUuid(), Set.of());
            if (wanted.equals(current)) {
                continue;
            }
            List<PlayerInfoUpdatePacket.Entry> entries = new ArrayList<>();
            for (Player target : online) {
                boolean hide = wanted.contains(target.getUuid());
                if (hide != current.contains(target.getUuid())) {
                    entries.add(entry(target, !hide, null));
                }
            }
            if (wanted.isEmpty()) {
                unlisted.remove(viewer.getUuid());
            } else {
                unlisted.put(viewer.getUuid(), wanted);
            }
            if (!entries.isEmpty()) {
                viewer.sendPacket(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_LISTED, entries));
            }
        }
    }

    /** An entry for one of the update actions; only the field of that action is sent. */
    private static PlayerInfoUpdatePacket.Entry entry(Player target, boolean listed, Component displayName) {
        return new PlayerInfoUpdatePacket.Entry(target.getUuid(), target.getUsername(), List.of(), listed, 0,
                GameMode.SURVIVAL, displayName, null, 0, true);
    }

    /** Clears the header and footer of every player and forgets the names (the tab list was turned off). */
    void reset(Collection<Player> online) {
        for (Player viewer : online) {
            if (headers.remove(viewer.getUuid()) != null) {
                viewer.sendPacket(new PlayerListHeaderAndFooterPacket(Component.empty(), Component.empty()));
            }
        }
        listing(false, online);
        if (!names.isEmpty()) {
            List<PlayerInfoUpdatePacket.Entry> entries = online.stream().map(target -> entry(target, true, null)).toList();
            if (!entries.isEmpty()) {
                PacketSendingUtils.sendGroupedPacket(online,
                        new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, entries));
            }
        }
        names.clear();
        knowNames.clear();
    }

    void forget(UUID player) {
        names.remove(player);
        knowNames.remove(player);
        headers.remove(player);
        unlisted.remove(player);
        unlisted.values().forEach(set -> set.remove(player));
    }

    /** How many name packets were sent, for tests and measurements. */
    int namePackets() {
        return namePackets;
    }
}
