package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.JoinStrategy;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Everything in {@code config.yml}, already validated.
 *
 * @param server     network settings and the server list entry
 * @param connection how players reach the server
 * @param world      which map to load and how
 * @param lobbies    how many lobby instances of that map to run
 * @param spawn      where players appear
 * @param protection what players may do in the lobby
 * @param operators  lower-case usernames with every permission (used when LuckPerms is disabled)
 */
public record LobbyConfig(
        Server server,
        Connection connection,
        World world,
        Lobbies lobbies,
        SpawnPoint spawn,
        Protection protection,
        Set<String> operators
) {

    /** Highest valid TCP port. */
    private static final int MAX_PORT = 65535;
    /** Largest supported max-players value. */
    private static final int MAX_PLAYERS_LIMIT = 10_000;
    /** Length of a full Minecraft day in ticks. */
    public static final int TICKS_PER_DAY = 24_000;
    /** Lowest and highest Y a world can use. */
    private static final int MIN_Y = -2048;
    private static final int MAX_Y = 2048;
    /** Chunk radius limits for preload and view distance. */
    private static final int MAX_CHUNK_RADIUS = 32;
    private static final int MIN_VIEW_DISTANCE = 2;
    /** Coordinates further than this from 0 are almost certainly typos. */
    private static final double MAX_COORDINATE = 30_000_000;
    /** Most lobby instances one server may run; more than this never fits a selector menu. */
    public static final int MAX_INSTANCES = 50;
    /** Allowed characters for the lobby name. */
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    /** {@code server:} section. */
    public record Server(String name, String host, int port, String motd, int maxPlayers) {
    }

    /** Connection options at the top of the file. */
    public record Connection(
            ConnectionMode mode,
            boolean onlineMode,
            boolean fetchSkinsForOfflinePlayers,
            String velocitySecret,
            List<String> bungeeGuardTokens
    ) {
        /** Hides secrets, so this record can be logged safely. */
        @Override
        public String toString() {
            return "Connection[mode=" + mode + ", onlineMode=" + onlineMode + "]";
        }
    }

    /** {@code world:} section. {@code time} is -1 for a normal day/night cycle. */
    public record World(String path, boolean convertAnvilToPolar, int preloadRadius, int viewDistance,
                        int time, int voidY) {
        /** True if the time of day should stay fixed. */
        public boolean fixedTime() {
            return time >= 0;
        }
    }

    /**
     * {@code lobbies:} section.
     *
     * @param instances          how many lobby instances to create, or {@link #AUTO}
     * @param playersPerInstance players one instance should hold; also decides {@code auto}
     * @param join               which instance a joining player is put in
     */
    public record Lobbies(int instances, int playersPerInstance, JoinStrategy join) {

        /** {@code instances: auto}: one instance per {@code players-per-instance} of max-players. */
        public static final int AUTO = 0;

        /** True if the number of instances follows {@code server.max-players}. */
        public boolean auto() {
            return instances == AUTO;
        }

        /** How many instances to create for this {@code server.max-players}. Always at least 1. */
        public int instanceCount(int maxPlayers) {
            if (!auto()) {
                return instances;
            }
            int needed = Math.ceilDiv(maxPlayers, playersPerInstance);
            return Math.clamp(needed, 1, MAX_INSTANCES);
        }
    }

    /** {@code spawn:} section. */
    public record SpawnPoint(double x, double y, double z, float yaw, float pitch) {
    }

    /** {@code protection:} section. {@code true} means players are allowed to do it. */
    public record Protection(boolean blockBreak, boolean blockPlace, boolean damage, boolean hunger,
                             boolean itemDrop) {
    }

    /** Reads {@code server.name}; falls back to the default if it has characters that are not allowed. */
    private static String serverName(ConfigReader reader) {
        String name = reader.string("server.name").trim();
        return SERVER_NAME.matcher(name).matches() ? name : reader.invalidValue("server.name", name,
                "letters, digits, - and _ (1-32 characters)");
    }

    /**
     * Reads and validates config.yml.
     *
     * @throws ConfigException for problems the server must not start with
     */
    public static LobbyConfig read(ConfigReader reader) throws ConfigException {
        Server server = new Server(
                serverName(reader),
                reader.string("server.host"),
                reader.integer("server.port", 1, MAX_PORT),
                reader.string("server.motd"),
                reader.integer("server.max-players", 1, MAX_PLAYERS_LIMIT));

        String modeName = reader.strictChoice("mode", ConnectionMode.configNames());
        if (modeName == null) {
            throw new ConfigException("config.yml: option 'mode' has an invalid value. Allowed: "
                    + String.join(", ", ConnectionMode.configNames().stream().sorted().toList())
                    + ". The server will not start with an unknown mode because that could leave it unprotected.");
        }
        ConnectionMode mode = ConnectionMode.fromConfigName(modeName);
        Connection connection = new Connection(
                mode,
                reader.bool("online-mode"),
                reader.bool("fetch-skins-for-offline-players"),
                reader.string("velocity-secret").trim(),
                reader.stringList("bungeeguard-tokens"));
        if (mode == ConnectionMode.VELOCITY && connection.velocitySecret().isEmpty()) {
            throw new ConfigException("config.yml: mode is 'velocity' but 'velocity-secret' is empty. "
                    + "Copy the text from the forwarding.secret file in your Velocity folder into 'velocity-secret'.");
        }

        World world = new World(
                reader.string("world.path"),
                reader.bool("world.convert-anvil-to-polar"),
                reader.integer("world.preload-radius", 1, MAX_CHUNK_RADIUS),
                reader.integer("world.view-distance", MIN_VIEW_DISTANCE, MAX_CHUNK_RADIUS),
                reader.integer("world.time", -1, TICKS_PER_DAY),
                reader.integer("world.void-y", MIN_Y, MAX_Y));

        Lobbies lobbies = lobbies(reader);

        SpawnPoint spawn = new SpawnPoint(
                reader.decimal("spawn.x", -MAX_COORDINATE, MAX_COORDINATE),
                reader.decimal("spawn.y", MIN_Y, MAX_Y),
                reader.decimal("spawn.z", -MAX_COORDINATE, MAX_COORDINATE),
                (float) reader.decimal("spawn.yaw", -360, 360),
                (float) reader.decimal("spawn.pitch", -90, 90));

        Protection protection = new Protection(
                reader.bool("protection.block-break"),
                reader.bool("protection.block-place"),
                reader.bool("protection.damage"),
                reader.bool("protection.hunger"),
                reader.bool("protection.item-drop"));

        Set<String> operators = reader.stringList("operators").stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        return new LobbyConfig(server, connection, world, lobbies, spawn, protection, operators);
    }

    /** Reads the {@code lobbies:} section. {@code instances} is a number or the word {@code auto}. */
    private static Lobbies lobbies(ConfigReader reader) {
        String wanted = reader.string("lobbies.instances", "1").strip();
        int instances;
        if (wanted.equalsIgnoreCase("auto")) {
            instances = Lobbies.AUTO;
        } else {
            try {
                instances = Integer.parseInt(wanted);
            } catch (NumberFormatException e) {
                instances = -1;
            }
            if (instances < 1 || instances > MAX_INSTANCES) {
                reader.reportInvalid("lobbies.instances", wanted, "a number from 1 to " + MAX_INSTANCES
                        + ", or \"auto\"");
                instances = 1;
            }
        }
        String joinName = reader.choice("lobbies.join", JoinStrategy.configNames());
        return new Lobbies(instances,
                reader.integer("lobbies.players-per-instance", 1, MAX_PLAYERS_LIMIT, 50),
                JoinStrategy.fromConfigName(joinName));
    }
}
