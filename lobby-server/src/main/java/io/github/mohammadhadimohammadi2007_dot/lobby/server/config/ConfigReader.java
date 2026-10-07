package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads typed values from a user's YAML file, falling back to the bundled default file.
 *
 * <p>Nothing here throws for user mistakes. A missing option uses the default from the bundled
 * file; an invalid value uses the default too. Both are recorded in {@link #warnings()} with a
 * message that names the file, the option and the allowed values.
 */
public final class ConfigReader {

    private final String fileName;
    private final ConfigurationNode user;
    private final ConfigurationNode defaults;
    private final List<String> warnings = new ArrayList<>();

    /**
     * @param fileName name shown in messages, e.g. {@code config.yml}
     * @param user     the file the server owner edits
     * @param defaults the bundled default file; must contain every option that is read
     */
    public ConfigReader(String fileName, ConfigurationNode user, ConfigurationNode defaults) {
        this.fileName = fileName;
        this.user = user;
        this.defaults = defaults;
    }

    /** Problems found so far, in a form that can be logged as-is. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** Reads any text value. */
    public String string(String path) {
        Object raw = userValue(path);
        if (raw == null) {
            return defaultString(path);
        }
        if (raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
            return String.valueOf(raw);
        }
        return invalid(path, raw, "a text value", defaultString(path));
    }

    /** Reads a whole number between {@code min} and {@code max} (both included). */
    public int integer(String path, int min, int max) {
        int fallback = (int) defaultNumber(path);
        Object raw = userValue(path);
        if (raw == null) {
            return fallback;
        }
        Long value = asLong(raw);
        if (value == null || value < min || value > max) {
            return invalid(path, raw, "a whole number from " + min + " to " + max, fallback);
        }
        return value.intValue();
    }

    /** Reads a number that may have decimals, between {@code min} and {@code max} (both included). */
    public double decimal(String path, double min, double max) {
        double fallback = defaultNumber(path);
        Object raw = userValue(path);
        if (raw == null) {
            return fallback;
        }
        Double value = asDouble(raw);
        if (value == null || value.isNaN() || value < min || value > max) {
            return invalid(path, raw, "a number from " + format(min) + " to " + format(max), fallback);
        }
        return value;
    }

    /** Reads {@code true} or {@code false}. */
    public boolean bool(String path) {
        boolean fallback = Boolean.TRUE.equals(asBoolean(requireDefault(path)));
        Object raw = userValue(path);
        if (raw == null) {
            return fallback;
        }
        Boolean value = asBoolean(raw);
        if (value == null) {
            return invalid(path, raw, "true or false", fallback);
        }
        return value;
    }

    /**
     * Reads one word out of a fixed set, ignoring upper/lower case.
     *
     * @return the chosen word in lower case
     */
    public String choice(String path, Set<String> allowed) {
        String fallback = defaultString(path).toLowerCase(Locale.ROOT);
        Object raw = userValue(path);
        if (raw == null) {
            return fallback;
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            return invalid(path, raw, "one of " + String.join(", ", allowed.stream().sorted().toList()), fallback);
        }
        return value;
    }

    /**
     * Like {@link #choice}, but tells the caller the value was invalid instead of falling back.
     *
     * @return the chosen word in lower case, or {@code null} if the user's value is not allowed
     */
    public String strictChoice(String path, Set<String> allowed) {
        Object raw = userValue(path);
        if (raw == null) {
            return choice(path, allowed);
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return allowed.contains(value) ? value : null;
    }

    /** Reads a list of text values, e.g. {@code ["a", "b"]}. Empty entries are skipped. */
    public List<String> stringList(String path) {
        List<String> fallback = listOf(requireDefaultNode(path));
        ConfigurationNode node = user.node(split(path));
        if (node.virtual()) {
            warnMissing(path, String.valueOf(fallback));
            return fallback;
        }
        if (!node.isList()) {
            return invalid(path, node.raw(), "a list like [\"a\", \"b\"]", fallback);
        }
        return listOf(node);
    }

    private static List<String> listOf(ConfigurationNode node) {
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            Object raw = child.raw();
            if (raw != null && !String.valueOf(raw).isBlank()) {
                values.add(String.valueOf(raw).trim());
            }
        }
        return List.copyOf(values);
    }

    /**
     * Returns the user's raw value, or {@code null} (with a warning) if the option is missing.
     * Configurate treats "option:" with nothing after it the same as a missing option.
     */
    private Object userValue(String path) {
        ConfigurationNode node = user.node(split(path));
        if (node.virtual()) {
            warnMissing(path, String.valueOf(requireDefault(path)));
            return null;
        }
        return node.raw();
    }

    private void warnMissing(String path, String defaultValue) {
        warnings.add(fileName + ": option '" + path + "' is missing or empty. Using the default: " + defaultValue
                + ". Tip: compare your file with the default one to add new options.");
    }

    private <T> T invalid(String path, Object raw, String allowed, T fallback) {
        warnings.add(fileName + ": option '" + path + "' has an invalid value '" + raw + "'. Allowed: "
                + allowed + ". Using the default: " + fallback);
        return fallback;
    }

    private String defaultString(String path) {
        return String.valueOf(requireDefault(path));
    }

    private double defaultNumber(String path) {
        Double value = asDouble(requireDefault(path));
        if (value == null) {
            throw new IllegalStateException("Bundled default for '" + path + "' in " + fileName + " is not a number");
        }
        return value;
    }

    private Object requireDefault(String path) {
        Object raw = requireDefaultNode(path).raw();
        if (raw == null) {
            throw new IllegalStateException("Bundled default for '" + path + "' in " + fileName + " is empty");
        }
        return raw;
    }

    private ConfigurationNode requireDefaultNode(String path) {
        ConfigurationNode node = defaults.node(split(path));
        if (node.virtual()) {
            // A bug in this project, not a user mistake: every option read must exist in the bundled file.
            throw new IllegalStateException("Bundled " + fileName + " has no default for '" + path + "'");
        }
        return node;
    }

    private static Object[] split(String path) {
        return path.split("\\.");
    }

    private static Long asLong(Object raw) {
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Double asDouble(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Boolean asBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text) {
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on" -> true;
                case "false", "no", "off" -> false;
                default -> null;
            };
        }
        return null;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
