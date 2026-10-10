package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.LegacyText;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's sidebar. Each refresh renders only the lines that are due and sends only the ones whose
 * text really changed, so a board where only the player count moves costs one small packet per player
 * per second. Players take turns within each interval ({@link Stagger}), so a second's work is spread
 * over its ten refreshes.
 *
 * <p>Only used on the board thread.
 */
final class SidebarService {

    /** What one player's client shows right now. */
    private static final class View {
        private @Nullable SidebarConfig.Board board;
        private SidebarPackets.Mode mode = SidebarPackets.Mode.SCORES;
        private Component title = Component.empty();
        private final List<Component> lines = new ArrayList<>();
    }

    private final SidebarPackets packets;
    private final ClientTiers tiers;
    private final PermissionService permissions;
    private final Map<UUID, View> views = new HashMap<>();
    private int linesSent;

    SidebarService(SidebarPackets packets, ClientTiers tiers, PermissionService permissions) {
        this.packets = packets;
        this.tiers = tiers;
        this.permissions = permissions;
    }

    /**
     * One refresh.
     *
     * @param previousTicks the tick of the last refresh, so a line is due when its interval boundary
     *                      fell between the two
     */
    void refresh(SidebarConfig config, Collection<Player> online, TextCache text, long previousTicks, long ticks) {
        for (Player player : online) {
            SidebarConfig.Board board = config.enabled() ? config.boardFor(player, permissions::hasPermission) : null;
            View view = views.computeIfAbsent(player.getUuid(), ignored -> new View());
            SidebarPackets.Mode mode = modeOf(player);
            if (board == null) {
                hide(player, view);
                continue;
            }
            boolean fresh = view.board != board || view.mode != mode;
            if (fresh) {
                hide(player, view);
                view.board = board;
                view.mode = mode;
                view.title = title(board, player, text, ticks);
                packets.show(player, view.title);
            } else if (Stagger.due(previousTicks, ticks, board.titleIntervalTicks(), player.getUuid())) {
                // The next frame, or the same one with fresh placeholders.
                Component title = title(board, player, text, ticks);
                if (!title.equals(view.title)) {
                    view.title = title;
                    packets.title(player, title);
                }
            }
            lines(player, view, board.lines(tiers.legacyText(player)), text, fresh, previousTicks, ticks);
        }
    }

    private void lines(Player player, View view, List<SidebarConfig.Line> wanted, TextCache text, boolean fresh,
                       long previousTicks, long ticks) {
        int count = wanted.size();
        boolean countChanged = count != view.lines.size();
        for (int i = 0; i < count; i++) {
            SidebarConfig.Line line = wanted.get(i);
            boolean exists = i < view.lines.size();
            if (exists && !fresh && !Stagger.due(previousTicks, ticks, line.intervalTicks(), player.getUuid())) {
                if (countChanged) {
                    packets.place(player, view.mode, i, count, view.lines.get(i));
                }
                continue;
            }
            Component rendered = text.render(line.text(), player);
            if (!exists) {
                view.lines.add(rendered);
                packets.line(player, view.mode, i, count, rendered, true);
                linesSent++;
            } else if (!rendered.equals(view.lines.get(i))) {
                view.lines.set(i, rendered);
                packets.line(player, view.mode, i, count, rendered, false);
                linesSent++;
                if (countChanged && view.mode != SidebarPackets.Mode.SCORES) {
                    packets.place(player, view.mode, i, count, rendered);
                }
            } else if (countChanged) {
                packets.place(player, view.mode, i, count, rendered);
            }
        }
        while (view.lines.size() > count) {
            packets.remove(player, view.mode, view.lines.size() - 1);
            view.lines.removeLast();
        }
    }

    private Component title(SidebarConfig.Board board, Player player, TextCache text, long ticks) {
        int frame = Stagger.index(ticks, board.titleIntervalTicks(), board.title().size(), player.getUuid());
        Component title = text.render(board.title().get(frame), player);
        return tiers.legacyText(player) ? LegacyText.limit(title, SidebarPackets.LEGACY_TITLE_LIMIT) : title;
    }

    private void hide(Player player, View view) {
        if (view.board != null) {
            packets.hide(player, view.mode, view.lines.size());
            view.board = null;
            view.lines.clear();
            view.title = Component.empty();
        }
    }

    /** Hides every sidebar (the scoreboard was turned off). */
    void hideAll(Collection<Player> online) {
        for (Player player : online) {
            View view = views.get(player.getUuid());
            if (view != null) {
                hide(player, view);
            }
        }
    }

    /** Forgets a player who left. */
    void forget(UUID player) {
        views.remove(player);
    }

    /** How many line updates were sent in total, for tests and measurements. */
    int linesSent() {
        return linesSent;
    }

    private SidebarPackets.Mode modeOf(Player player) {
        if (tiers.scoreDisplayNames(player)) {
            return SidebarPackets.Mode.SCORES;
        }
        return tiers.legacyText(player) ? SidebarPackets.Mode.SHORT_TEAMS : SidebarPackets.Mode.TEAMS;
    }
}
