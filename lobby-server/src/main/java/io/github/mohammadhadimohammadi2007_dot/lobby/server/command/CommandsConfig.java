package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * {@code commands.yml}: which commands each group may see and use. See {@link CommandWhitelist}.
 *
 * @param enabled false lets everyone see every command their permissions allow
 * @param groups  by group name (lower case), in file order
 */
public record CommandsConfig(boolean enabled, Map<String, Group> groups) {

    /** The group every player is in. */
    public static final String DEFAULT_GROUP = "default";
    /** In a list: every command. */
    public static final String ALL = "*";

    public CommandsConfig {
        groups = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }

    /**
     * One group's entries.
     *
     * @param weight  used when the permission plugin gives the group no weight (without LuckPerms)
     * @param allowed command names (lower case), or {@link #ALL}
     * @param denied  command names written as {@code -name}
     */
    public record Group(String name, OptionalInt weight, Set<String> allowed, Set<String> denied) {

        public Group {
            allowed = Set.copyOf(allowed);
            denied = Set.copyOf(denied);
        }
    }

    /** Reads commands.yml. */
    public static CommandsConfig read(ConfigReader reader) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (String name : reader.keys("groups")) {
            String path = "groups." + name;
            String lower = name.toLowerCase(Locale.ROOT);
            int weight = reader.integer(path + ".weight", -1_000_000, 1_000_000, Integer.MIN_VALUE);
            Set<String> allowed = new HashSet<>();
            Set<String> denied = new HashSet<>();
            for (String entry : reader.stringList(path + ".commands", List.of())) {
                String command = entry.strip().toLowerCase(Locale.ROOT);
                boolean deny = command.startsWith("-");
                if (deny) {
                    command = command.substring(1).strip();
                }
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                if (command.isEmpty()) {
                    continue;
                }
                (deny ? denied : allowed).add(command);
            }
            groups.put(lower, new Group(lower,
                    weight == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(weight), allowed, denied));
        }
        if (!groups.containsKey(DEFAULT_GROUP)) {
            reader.addWarning(reader.fileName() + ": there is no 'default' group, so players without a listed group"
                    + " see no commands at all.");
        }
        return new CommandsConfig(reader.bool("enabled", true), groups);
    }
}
