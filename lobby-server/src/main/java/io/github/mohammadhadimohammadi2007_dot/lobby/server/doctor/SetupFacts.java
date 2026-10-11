package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything {@link SetupChecks} looks at, collected in one go on the tick thread. Plain data, so the checks
 * can be tested without a server.
 *
 * @param mode                 how players connect
 * @param onlineMode           standalone online mode (Mojang checks names)
 * @param velocitySecretSet    a velocity-secret is configured
 * @param bungeeGuardTokens    how many BungeeGuard tokens are configured
 * @param worldFormat          what was loaded
 * @param worldName            the map's name
 * @param worldPath            {@code world.path}
 * @param chunks               chunks loaded
 * @param spawnIsDefault       {@code spawn:} is still the bundled value
 * @param spawnOnGround        the block under spawn is solid
 * @param operators            how many operators there are
 * @param bridgeEnabled        {@code bridge.enabled} in integrations.yml
 * @param bridgeSeen           the bridge has sent network data since the start
 * @param bridgeSilentMillis   time since the bridge last sent something, or -1 if never
 * @param groups               server groups the bridge reported
 * @param proxy                what the proxy told about itself (bridge 0.4+)
 * @param playersJoined        players who joined since the start
 * @param uptimeMillis         time since the start
 * @param integrations         every optional integration
 * @param databaseError        why the database could not be reached, or {@code null}
 * @param network              the network-name check
 * @param referencedGroups     groups that NPCs, holograms, portals, menus or hotbar items lead to
 * @param menus                menu names in menus.yml
 * @param sidebarEnabled       scoreboard on
 * @param tabEnabled           tab list on
 * @param commandsWhitelist    commands.yml on
 * @param counts               "NPCs", "holograms", "portals" to how many there are
 * @param brokenEntries        data file to the names of its entries that could not be read
 * @param configWarnings       warnings from reading the config files
 */
public record SetupFacts(
        ConnectionMode mode,
        boolean onlineMode,
        boolean velocitySecretSet,
        int bungeeGuardTokens,
        WorldFormat worldFormat,
        String worldName,
        String worldPath,
        int chunks,
        boolean spawnIsDefault,
        boolean spawnOnGround,
        int operators,
        boolean bridgeEnabled,
        boolean bridgeSeen,
        long bridgeSilentMillis,
        Map<String, List<String>> groups,
        Optional<BridgeMessage.ProxyInfo> proxy,
        int playersJoined,
        long uptimeMillis,
        List<IntegrationStatus> integrations,
        @Nullable String databaseError,
        NetworkCheck.Report network,
        Set<String> referencedGroups,
        Set<String> menus,
        boolean sidebarEnabled,
        boolean tabEnabled,
        boolean commandsWhitelist,
        Map<String, Integer> counts,
        Map<String, List<String>> brokenEntries,
        List<String> configWarnings
) {

    public SetupFacts {
        groups = Map.copyOf(groups);
        integrations = List.copyOf(integrations);
        referencedGroups = Set.copyOf(referencedGroups);
        menus = Set.copyOf(menus);
        counts = Map.copyOf(counts);
        brokenEntries = Map.copyOf(brokenEntries);
        configWarnings = List.copyOf(configWarnings);
    }

    /** True if players come through Velocity or BungeeCord. */
    public boolean behindProxy() {
        return mode.behindProxy();
    }
}
