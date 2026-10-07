package io.github.mohammadhadimohammadi2007_dot.lobby.luckperms;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import me.lucko.luckperms.common.config.generic.adapter.StringBasedConfigurationAdapter;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Gives LuckPerms its settings from integrations.yml, so server owners do not need a separate
 * LuckPerms config file. Everything not listed here uses LuckPerms' defaults.
 */
final class LobbyConfigAdapter extends StringBasedConfigurationAdapter {

    /** LuckPerms keeps its own small pool; the lobby's shared pool is separate. */
    private static final String LUCKPERMS_POOL_SIZE = "4";

    private final LuckPermsPlugin plugin;
    private final Map<String, String> values;

    LobbyConfigAdapter(LuckPermsPlugin plugin, IntegrationsConfig.Database database,
                       IntegrationsConfig.LuckPerms luckPerms) {
        this.plugin = plugin;
        this.values = Map.of(
                "server", luckPerms.serverName(),
                "storage-method", "mariadb",
                "data.address", database.host() + ":" + database.port(),
                "data.database", database.database(),
                "data.username", database.username(),
                "data.password", database.password(),
                "data.table-prefix", luckPerms.tablePrefix(),
                "data.pool-settings.maximum-pool-size", LUCKPERMS_POOL_SIZE,
                "messaging-service", luckPerms.messagingService());
    }

    @Override
    protected @Nullable String resolveValue(String path) {
        return values.get(path);
    }

    @Override
    public LuckPermsPlugin getPlugin() {
        return plugin;
    }

    @Override
    public void reload() {
        // Values come from integrations.yml, which needs a restart to change.
    }
}
