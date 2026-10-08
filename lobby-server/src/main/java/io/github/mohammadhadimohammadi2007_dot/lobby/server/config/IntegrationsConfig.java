package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Everything in {@code integrations.yml}, already validated.
 *
 * @param database      shared MariaDB connection settings
 * @param luckPerms     LuckPerms settings
 * @param liteBans      LiteBans settings
 * @param skinsRestorer SkinsRestorer settings
 * @param bridge        proxy bridge settings
 * @param signedVelocity SignedVelocity sync settings
 */
public record IntegrationsConfig(
        Database database,
        LuckPerms luckPerms,
        LiteBans liteBans,
        SkinsRestorer skinsRestorer,
        Bridge bridge,
        SignedVelocity signedVelocity
) {

    private static final int MAX_PORT = 65535;
    private static final int MAX_POOL_SIZE = 50;
    private static final int MAX_CHECK_INTERVAL_SECONDS = 60;
    /** Table prefixes end up inside SQL, so only allow safe characters. */
    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_]{0,32}");

    /** {@code database:} section. */
    public record Database(String host, int port, String database, String username, String password, int poolSize) {
        /** Hides the password, so this record can be logged safely. */
        @Override
        public String toString() {
            return "Database[" + username + "@" + host + ":" + port + "/" + database + "]";
        }
    }

    /** {@code luckperms:} section. */
    public record LuckPerms(boolean enabled, String serverName, String tablePrefix, String messagingService) {
    }

    /** {@code litebans:} section. */
    public record LiteBans(boolean enabled, String tablePrefix, String serverName, int checkIntervalSeconds) {
    }

    /** {@code skinsrestorer:} section. */
    public record SkinsRestorer(boolean enabled, String tablePrefix) {
    }

    /** {@code bridge:} section. */
    public record Bridge(boolean enabled) {
    }

    /** {@code signedvelocity:} section. */
    public record SignedVelocity(boolean enabled) {
    }

    /** True if any enabled integration needs the shared database pool. */
    public boolean needsDatabase() {
        return luckPerms.enabled || liteBans.enabled || skinsRestorer.enabled;
    }

    /**
     * Reads and validates integrations.yml.
     *
     * @throws ConfigException if a table prefix contains characters that are not allowed
     */
    public static IntegrationsConfig read(ConfigReader reader) throws ConfigException {
        Database database = new Database(
                reader.string("database.host"),
                reader.integer("database.port", 1, MAX_PORT),
                reader.string("database.database"),
                reader.string("database.username"),
                reader.string("database.password"),
                reader.integer("database.pool-size", 1, MAX_POOL_SIZE));
        LuckPerms luckPerms = new LuckPerms(
                reader.bool("luckperms.enabled"),
                reader.string("luckperms.server-name"),
                prefix(reader, "luckperms.table-prefix"),
                reader.choice("luckperms.messaging-service", Set.of("sql", "pluginmsg")));
        LiteBans liteBans = new LiteBans(
                reader.bool("litebans.enabled"),
                prefix(reader, "litebans.table-prefix"),
                reader.string("litebans.server-name").trim(),
                reader.integer("litebans.check-interval", 1, MAX_CHECK_INTERVAL_SECONDS));
        SkinsRestorer skinsRestorer = new SkinsRestorer(
                reader.bool("skinsrestorer.enabled"),
                prefix(reader, "skinsrestorer.table-prefix"));
        Bridge bridge = new Bridge(reader.bool("bridge.enabled"));
        SignedVelocity signedVelocity = new SignedVelocity(reader.bool("signedvelocity.enabled"));
        return new IntegrationsConfig(database, luckPerms, liteBans, skinsRestorer, bridge, signedVelocity);
    }

    private static String prefix(ConfigReader reader, String path) throws ConfigException {
        String value = reader.string(path).trim();
        if (!SAFE_PREFIX.matcher(value).matches()) {
            throw new ConfigException("integrations.yml: option '" + path + "' has an invalid value '" + value
                    + "'. Allowed: letters, numbers and _ only (max 32 characters).");
        }
        return value;
    }
}
