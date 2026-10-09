package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns FancyNpcs actions into this lobby's, for {@code /npc import} and for admins who type
 * {@code /npc action <npc> any_click add message Hello} the way FancyNpcs taught them.
 *
 * <p>Checked against FancyNpcs' own action classes:
 *
 * <table>
 *   <tr><th>FancyNpcs</th><th>Here</th></tr>
 *   <tr><td>{@code message}, {@code player_command}, {@code console_command}, {@code need_permission}</td>
 *       <td>the same</td></tr>
 *   <tr><td>{@code send_to_server <server>}</td><td>{@code connect: <server>}</td></tr>
 *   <tr><td>{@code play_sound <sound>}</td><td>{@code sound: <sound>}</td></tr>
 *   <tr><td>{@code wait <seconds>}</td><td>{@code wait: <ticks>}, so the number is multiplied by 20</td></tr>
 *   <tr><td>{@code execute_random_action}</td><td>a {@code random:} section holding every action after
 *       it, because FancyNpcs runs one of the following actions and then stops</td></tr>
 *   <tr><td>{@code block_until_done}</td><td>nothing: the click cooldown already stops a list from
 *       running twice at once</td></tr>
 *   <tr><td>{@code player_command_as_op}</td><td>refused: it makes the player an operator for a moment,
 *       which a lobby must never do</td></tr>
 * </table>
 */
final class FancyNpcsActions {

    private static final int TICKS_PER_SECOND = 20;
    private static final String RANDOM = "execute_random_action";
    private static final String BLOCK_UNTIL_DONE = "block_until_done";
    private static final String AS_OP = "player_command_as_op";

    private FancyNpcsActions() {
    }

    /** One FancyNpcs action: its type and value. */
    record Action(String type, String value) {
    }

    /**
     * What an admin typed after {@code add}: this lobby's form ({@code message: Hello}) is kept as it is;
     * FancyNpcs' form ({@code message Hello}) is translated.
     *
     * @throws ActionParser.ActionException if it cannot be used, with the reason
     */
    static String typed(String written) throws ActionParser.ActionException {
        String text = written.strip();
        int space = text.indexOf(' ');
        String firstWord = space < 0 ? text : text.substring(0, space);
        // "message: Hello" is this lobby's form: the colon says so. Without one it may be FancyNpcs' form.
        String line = firstWord.endsWith(":") ? null
                : line(new Action(firstWord, space < 0 ? "" : text.substring(space + 1)));
        if (line == null && isFancyType(firstWord)) {
            throw new ActionParser.ActionException(switch (firstWord.toLowerCase(Locale.ROOT)) {
                case AS_OP -> "player_command_as_op makes the player an operator for a moment, which this lobby"
                        + " never does; use console_command";
                case BLOCK_UNTIL_DONE -> "block_until_done is not needed here: the click cooldown already stops"
                        + " a list from running twice at once";
                default -> "execute_random_action is written as a random: section here; add it in the file";
            });
        }
        String result = line == null ? text : line;
        // The parser explains anything that is still wrong, such as an unknown type.
        ActionParser.parse(result);
        return result;
    }

    /**
     * Translates a whole FancyNpcs list. {@code problems} gets one entry per action that could not be
     * used, so the import can say so instead of dropping it in silence.
     */
    static List<Object> list(List<Action> actions, List<String> problems) {
        List<Object> entries = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            String type = action.type().toLowerCase(Locale.ROOT);
            if (type.equals(RANDOM)) {
                List<Object> rest = list(actions.subList(i + 1, actions.size()), problems);
                if (!rest.isEmpty()) {
                    entries.add(Map.of("random", rest));
                }
                return entries;
            }
            if (type.equals(BLOCK_UNTIL_DONE)) {
                continue;
            }
            if (type.equals(AS_OP)) {
                problems.add("player_command_as_op \"" + action.value() + "\" was left out: it makes the player"
                        + " an operator for a moment. Use console_command instead");
                continue;
            }
            String line = line(action);
            if (line == null) {
                problems.add("the action type '" + action.type() + "' does not exist here");
                continue;
            }
            try {
                ActionParser.parse(line);
                entries.add(line);
            } catch (ActionParser.ActionException e) {
                problems.add("\"" + line + "\": " + e.getMessage());
            }
        }
        return entries;
    }

    /** This lobby's line for one simple FancyNpcs action, or {@code null} if there is none. */
    static @Nullable String line(Action action) {
        String value = action.value().strip();
        return switch (action.type().toLowerCase(Locale.ROOT)) {
            case "message", "player_command", "console_command", "need_permission" ->
                    action.type().toLowerCase(Locale.ROOT) + ": " + value;
            case "send_to_server" -> "connect: " + value;
            case "play_sound" -> "sound: " + value;
            case "wait" -> "wait: " + seconds(value) * TICKS_PER_SECOND;
            default -> null;
        };
    }

    private static boolean isFancyType(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "send_to_server", "play_sound", RANDOM, BLOCK_UNTIL_DONE, AS_OP -> true;
            default -> false;
        };
    }

    private static int seconds(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
