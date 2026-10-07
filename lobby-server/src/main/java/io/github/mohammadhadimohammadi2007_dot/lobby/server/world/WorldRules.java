package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Weather;

/** Applies time of day and weather from config.yml to an instance. Safe to call again after a reload. */
public final class WorldRules {

    private WorldRules() {
    }

    /** Sets fixed time (or a normal cycle) and clear weather. */
    public static void apply(Instance instance, LobbyConfig.World settings) {
        var clock = instance.defaultClock();
        if (clock != null) {
            if (settings.fixedTime()) {
                clock.pause();
                clock.time(settings.time());
            } else {
                clock.resume();
            }
        }
        instance.setWeather(Weather.CLEAR);
    }
}
