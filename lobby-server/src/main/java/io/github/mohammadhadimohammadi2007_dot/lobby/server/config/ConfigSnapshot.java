package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import java.util.List;

/**
 * All config files loaded together at one moment.
 *
 * @param config       config.yml
 * @param integrations integrations.yml
 * @param messages     messages.yml
 * @param warnings     non-fatal problems found while loading, ready to be logged
 */
public record ConfigSnapshot(LobbyConfig config, IntegrationsConfig integrations, Messages messages,
                             List<String> warnings) {
    public ConfigSnapshot {
        warnings = List.copyOf(warnings);
    }
}
