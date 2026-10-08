package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.ActionBarAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.CloseMenuAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.ConnectAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.ConsoleCommandAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.LobbyAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.MessageAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.NeedPermissionAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.OpenMenuAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.PlayerCommandAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.RandomAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.SoundAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.TeleportAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.TitleAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.TransferAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.WaitAction;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads actions from configs and data files. Each entry is a string {@code "type: argument"} (or just
 * {@code "type"}), except {@code random}, which holds a list of actions.
 */
public final class ActionParser {

    /** Thrown for an entry that cannot be read; the message says what is wrong in plain words. */
    public static final class ActionException extends Exception {
        public ActionException(String message) {
            super(message);
        }
    }

    private interface Factory {
        Action create(String argument) throws ActionException;
    }

    private static final String RANDOM = "random";
    private static final int MAX_WAIT_TICKS = 20 * 60 * 10;
    private static final int MAX_TITLE_TICKS = 20 * 60;
    private static final int MAX_LOBBY = 100;
    private static final int MAX_PORT = 65_535;
    private static final float MAX_VOLUME = 10f;
    private static final float MIN_PITCH = 0.5f;
    private static final float MAX_PITCH = 2f;
    private static final int DEFAULT_FADE_IN = 10;
    private static final int DEFAULT_STAY = 70;
    private static final int DEFAULT_FADE_OUT = 20;
    private static final Map<String, Factory> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("message", argument -> new MessageAction(required(argument, "the text")));
        TYPES.put("player_command", argument -> new PlayerCommandAction(command(argument)));
        TYPES.put("console_command", argument -> new ConsoleCommandAction(command(argument)));
        TYPES.put("connect", argument -> new ConnectAction(word(argument, "a server name"), false));
        TYPES.put("connect_group", argument -> new ConnectAction(word(argument, "a group name"), true));
        TYPES.put("lobby", argument -> new LobbyAction(integer(argument, 1, MAX_LOBBY, "a lobby number")));
        TYPES.put("open_menu", argument -> new OpenMenuAction(word(argument, "a menu name")));
        TYPES.put("close_menu", argument -> new CloseMenuAction());
        TYPES.put("sound", ActionParser::sound);
        TYPES.put("title", ActionParser::title);
        TYPES.put("actionbar", argument -> new ActionBarAction(required(argument, "the text")));
        TYPES.put("teleport", ActionParser::teleport);
        TYPES.put("wait", argument -> new WaitAction(integer(argument, 1, MAX_WAIT_TICKS, "a number of ticks")));
        TYPES.put("need_permission", ActionParser::needPermission);
        TYPES.put("transfer", ActionParser::transfer);
    }

    private ActionParser() {
    }

    /** Every action type name, for tab completion and help. */
    public static List<String> typeNames() {
        List<String> names = new ArrayList<>(TYPES.keySet());
        names.add(RANDOM);
        return names;
    }

    /**
     * Reads a list of actions. Broken entries are skipped and reported through {@code warn}, naming the
     * option and the position, so one typo never disables the rest.
     *
     * @param option where the list comes from, e.g. {@code menus.yml: servers.items.bedwars.actions}
     */
    public static ActionList parseList(List<?> entries, long cooldownMillis, String option, Consumer<String> warn) {
        List<Action> actions = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            try {
                actions.add(parse(entries.get(i)));
            } catch (ActionException e) {
                warn.accept(option + " (entry " + (i + 1) + "): " + e.getMessage() + ". This action is skipped.");
            }
        }
        return new ActionList(actions, cooldownMillis);
    }

    /** Reads one entry: a string, or a {@code random:} map with a list of actions. */
    public static Action parse(Object entry) throws ActionException {
        if (entry instanceof Map<?, ?> map) {
            if (map.size() == 1 && RANDOM.equals(String.valueOf(map.keySet().iterator().next()).toLowerCase(Locale.ROOT))) {
                Object options = map.values().iterator().next();
                if (!(options instanceof List<?> list) || list.isEmpty()) {
                    throw new ActionException("'random' needs a list of actions below it");
                }
                List<Action> parsed = new ArrayList<>();
                for (Object option : list) {
                    parsed.add(parse(option));
                }
                return new RandomAction(parsed);
            }
            throw new ActionException("expected an action like \"message: Hello\", found " + map);
        }
        if (!(entry instanceof String text) || text.isBlank()) {
            throw new ActionException("expected an action like \"message: Hello\", found '" + entry + "'");
        }
        return parse(text);
    }

    /** Reads one {@code "type: argument"} string. */
    public static Action parse(String line) throws ActionException {
        String trimmed = line.strip();
        int colon = trimmed.indexOf(':');
        String type = (colon < 0 ? trimmed : trimmed.substring(0, colon)).strip().toLowerCase(Locale.ROOT);
        String argument = colon < 0 ? "" : trimmed.substring(colon + 1).strip();
        if (type.equals(RANDOM)) {
            throw new ActionException("'random' needs a list of actions below it, not text on the same line");
        }
        Factory factory = TYPES.get(type);
        if (factory == null) {
            throw new ActionException("unknown action '" + type + "'" + suggestion(type));
        }
        return factory.create(argument);
    }

    /** The config form of an action: a string, or a map for {@code random}. Used when saving data files. */
    public static Object toConfig(Action action) {
        if (action instanceof RandomAction random) {
            return Map.of(RANDOM, random.options().stream().map(ActionParser::toConfig).toList());
        }
        return action.describe();
    }

    private static Action sound(String argument) throws ActionException {
        String[] parts = required(argument, "a sound name").split("\\s+");
        Key key;
        try {
            key = Key.key(parts[0].toLowerCase(Locale.ROOT));
        } catch (InvalidKeyException e) {
            throw new ActionException("'" + parts[0] + "' is not a valid sound name");
        }
        float volume = parts.length > 1 ? decimal(parts[1], 0, MAX_VOLUME, "the volume") : 1f;
        float pitch = parts.length > 2 ? decimal(parts[2], MIN_PITCH, MAX_PITCH, "the pitch") : 1f;
        return new SoundAction(key, volume, pitch);
    }

    private static Action title(String argument) throws ActionException {
        String[] parts = required(argument, "the title text").split("\\|", -1);
        String subtitle = parts.length > 1 ? parts[1].strip() : "";
        int fadeIn = DEFAULT_FADE_IN;
        int stay = DEFAULT_STAY;
        int fadeOut = DEFAULT_FADE_OUT;
        if (parts.length > 2 && !parts[2].isBlank()) {
            String[] times = parts[2].strip().split("\\s+");
            if (times.length != 3) {
                throw new ActionException("the times must be three numbers: fade in, stay, fade out (in ticks)");
            }
            fadeIn = integer(times[0], 0, MAX_TITLE_TICKS, "fade in");
            stay = integer(times[1], 0, MAX_TITLE_TICKS, "stay");
            fadeOut = integer(times[2], 0, MAX_TITLE_TICKS, "fade out");
        }
        return new TitleAction(parts[0].strip(), subtitle, fadeIn, stay, fadeOut);
    }

    private static Action teleport(String argument) throws ActionException {
        String value = required(argument, "\"spawn\" or x y z [yaw pitch]");
        if (value.equalsIgnoreCase("spawn")) {
            return new TeleportAction(null);
        }
        String[] parts = value.split("\\s+");
        if (parts.length != 3 && parts.length != 5) {
            throw new ActionException("teleport needs \"spawn\" or x y z [yaw pitch]");
        }
        double[] numbers = new double[5];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Double.parseDouble(parts[i]);
            } catch (NumberFormatException e) {
                throw new ActionException("'" + parts[i] + "' is not a number");
            }
        }
        return new TeleportAction(new Pos(numbers[0], numbers[1], numbers[2], (float) numbers[3], (float) numbers[4]));
    }

    private static Action needPermission(String argument) throws ActionException {
        String value = required(argument, "a permission");
        int space = value.indexOf(' ');
        String permission = space < 0 ? value : value.substring(0, space);
        String message = space < 0 ? null : value.substring(space + 1).strip();
        return new NeedPermissionAction(permission, message == null || message.isEmpty() ? null : message);
    }

    private static Action transfer(String argument) throws ActionException {
        String[] parts = required(argument, "a server address").split("[\\s:]+");
        if (parts.length > 2) {
            throw new ActionException("transfer needs a host and an optional port, e.g. \"play.example.com 25565\"");
        }
        int port = parts.length > 1 ? integer(parts[1], 1, MAX_PORT, "the port") : TransferAction.DEFAULT_PORT;
        return new TransferAction(parts[0], port);
    }

    private static String required(String argument, String what) throws ActionException {
        if (argument.isBlank()) {
            throw new ActionException("missing " + what + " after the ':'");
        }
        return argument;
    }

    private static String word(String argument, String what) throws ActionException {
        String value = required(argument, what);
        if (value.contains(" ")) {
            throw new ActionException("expected " + what + " without spaces, found '" + value + "'");
        }
        return value;
    }

    private static String command(String argument) throws ActionException {
        String value = required(argument, "a command");
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static int integer(String text, int min, int max, String what) throws ActionException {
        try {
            int value = Integer.parseInt(text.strip());
            if (value < min || value > max) {
                throw new ActionException(what + " must be between " + min + " and " + max + ", found " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ActionException("expected " + what + " (a whole number), found '" + text + "'");
        }
    }

    private static float decimal(String text, float min, float max, String what) throws ActionException {
        try {
            float value = Float.parseFloat(text.strip());
            if (value < min || value > max) {
                throw new ActionException(what + " must be between " + min + " and " + max + ", found " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ActionException("expected " + what + " (a number), found '" + text + "'");
        }
    }

    /** " (did you mean 'connect'?)" for small typos, otherwise the list of types. */
    private static String suggestion(String type) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String name : typeNames()) {
            int distance = distance(type, name);
            if (distance < bestDistance) {
                best = name;
                bestDistance = distance;
            }
        }
        if (best != null && bestDistance <= 2) {
            return " (did you mean '" + best + "'?)";
        }
        return ". Known actions: " + String.join(", ", typeNames());
    }

    /** Levenshtein distance. */
    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
