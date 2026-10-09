package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.LegacyText;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.DisplayScoreboardPacket;
import net.minestom.server.network.packet.server.play.ResetScorePacket;
import net.minestom.server.network.packet.server.play.ScoreboardObjectivePacket;
import net.minestom.server.network.packet.server.play.UpdateScorePacket;
import net.minestom.server.scoreboard.Sidebar;

/**
 * The packets of one player's sidebar, in the form their client understands.
 *
 * <ul>
 *   <li>1.20.3+ ({@link Mode#SCORES}): each line is the display name of its score, and the red numbers
 *       are hidden;</li>
 *   <li>1.13-1.20.2 ({@link Mode#TEAMS}): each line is an invisible entry in a team of its own, whose
 *       prefix is the text, since these clients show a score's entry name and nothing else;</li>
 *   <li>1.8-1.12 ({@link Mode#SHORT_TEAMS}): the same, with the text split over the 16-character
 *       prefix and suffix.</li>
 * </ul>
 * Minestom's own {@code Sidebar} sends the same packets to every viewer, which cannot work here, so these
 * are built by hand. Teams go through {@link TeamManager}, the one place that makes teams.
 */
final class SidebarPackets {

    /** How a client gets its lines. */
    enum Mode {
        SCORES,
        TEAMS,
        SHORT_TEAMS
    }

    static final String OBJECTIVE = "lobby";
    /** Old clients allow 32 characters in an objective's title. */
    static final int LEGACY_TITLE_LIMIT = 32;
    private static final byte CREATE = 0;
    private static final byte REMOVE = 1;
    private static final byte UPDATE = 2;
    private static final byte SIDEBAR_SLOT = 1;
    private static final String TEAM_PREFIX = "sb";

    private final TeamManager teams;

    SidebarPackets(TeamManager teams) {
        this.teams = teams;
    }

    /** Creates the objective and shows it in the sidebar. */
    void show(Player viewer, Component title) {
        viewer.sendPacket(new ScoreboardObjectivePacket(OBJECTIVE, CREATE, title,
                ScoreboardObjectivePacket.Type.INTEGER, Sidebar.NumberFormat.blank()));
        viewer.sendPacket(new DisplayScoreboardPacket(SIDEBAR_SLOT, OBJECTIVE));
    }

    void title(Player viewer, Component title) {
        viewer.sendPacket(new ScoreboardObjectivePacket(OBJECTIVE, UPDATE, title,
                ScoreboardObjectivePacket.Type.INTEGER, Sidebar.NumberFormat.blank()));
    }

    /** Removes the objective and the line teams. */
    void hide(Player viewer, Mode mode, int lines) {
        viewer.sendPacket(new ScoreboardObjectivePacket(OBJECTIVE, REMOVE, Component.empty(),
                ScoreboardObjectivePacket.Type.INTEGER, null));
        if (mode != Mode.SCORES) {
            for (int i = 0; i < lines; i++) {
                teams.removePrivate(viewer, TEAM_PREFIX + i);
            }
        }
    }

    /**
     * Sets line {@code index} of {@code count}: its text and its place (the score, highest at the top).
     *
     * @param fresh true if the line did not exist before, so its team must be created
     */
    void line(Player viewer, Mode mode, int index, int count, Component text, boolean fresh) {
        String entry = entry(index);
        int score = count - index;
        if (mode == Mode.SCORES) {
            viewer.sendPacket(new UpdateScorePacket(entry, OBJECTIVE, score, text, null));
            return;
        }
        Component prefix = text;
        Component suffix = Component.empty();
        if (mode == Mode.SHORT_TEAMS) {
            LegacyText.Split split = LegacyText.split(text, TeamManager.LEGACY_LIMIT);
            prefix = split.first();
            suffix = split.second();
        }
        teams.sendPrivate(viewer, TEAM_PREFIX + index, fresh, prefix, suffix, entry);
        if (fresh) {
            viewer.sendPacket(new UpdateScorePacket(entry, OBJECTIVE, score, null, null));
        }
    }

    /** Moves a line to another place without changing its text (the number of lines changed). */
    void place(Player viewer, Mode mode, int index, int count, Component text) {
        String entry = entry(index);
        viewer.sendPacket(new UpdateScorePacket(entry, OBJECTIVE, count - index, mode == Mode.SCORES ? text : null,
                null));
    }

    /** Removes line {@code index}. */
    void remove(Player viewer, Mode mode, int index) {
        viewer.sendPacket(new ResetScorePacket(entry(index), OBJECTIVE));
        if (mode != Mode.SCORES) {
            teams.removePrivate(viewer, TEAM_PREFIX + index);
        }
    }

    /**
     * The invisible name line {@code index} is known by: a colour code and a reset. Unique per line, shown
     * as nothing, and always fewer than 16 characters.
     */
    static String entry(int index) {
        return "§" + Integer.toHexString(index) + "§r";
    }
}
