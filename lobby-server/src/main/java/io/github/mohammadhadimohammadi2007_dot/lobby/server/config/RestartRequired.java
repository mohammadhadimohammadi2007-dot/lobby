package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Finds options that changed between two loads but only take effect after a restart.
 *
 * <p>Everything not listed here is applied live by {@code /lobby reload}: MOTD, max players,
 * spawn, protections, time of day, void Y, operators, all messages, and chat.yml except storage.
 */
public final class RestartRequired {

    private RestartRequired() {
    }

    /** Returns the names of changed options that need a restart, in file order. */
    public static List<String> changedOptions(ConfigSnapshot before, ConfigSnapshot after) {
        List<String> changed = new ArrayList<>();
        LobbyConfig a = before.config();
        LobbyConfig b = after.config();
        check(changed, "server.host", a.server().host(), b.server().host());
        check(changed, "server.port", a.server().port(), b.server().port());
        check(changed, "mode", a.connection().mode(), b.connection().mode());
        check(changed, "online-mode", a.connection().onlineMode(), b.connection().onlineMode());
        check(changed, "fetch-skins-for-offline-players",
                a.connection().fetchSkinsForOfflinePlayers(), b.connection().fetchSkinsForOfflinePlayers());
        check(changed, "velocity-secret", a.connection().velocitySecret(), b.connection().velocitySecret());
        check(changed, "bungeeguard-tokens", a.connection().bungeeGuardTokens(), b.connection().bungeeGuardTokens());
        check(changed, "world.path", a.world().path(), b.world().path());
        check(changed, "world.convert-anvil-to-polar", a.world().convertAnvilToPolar(), b.world().convertAnvilToPolar());
        check(changed, "world.preload-radius", a.world().preloadRadius(), b.world().preloadRadius());
        check(changed, "world.view-distance", a.world().viewDistance(), b.world().viewDistance());
        // integrations.yml is read once at startup.
        check(changed, "integrations.yml", before.integrations(), after.integrations());
        // Chat storage (tables, log files) is opened once at startup; everything else in chat.yml reloads.
        check(changed, "chat.yml storage", before.chat().storage(), after.chat().storage());
        return changed;
    }

    private static void check(List<String> changed, String option, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changed.add(option);
        }
    }
}
