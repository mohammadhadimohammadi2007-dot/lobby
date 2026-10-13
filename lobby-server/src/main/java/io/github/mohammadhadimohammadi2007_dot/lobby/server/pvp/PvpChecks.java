package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.Check;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The PvP lines of {@code /setup-lobby setup}. Pure. */
public final class PvpChecks {

    private static final String TITLE = "PvP area";

    private PvpChecks() {
    }

    /**
     * @param behindProxy        the lobby runs behind a proxy with the bridge
     * @param bridgeKeepsTags    the bridge is new enough to block /server during a fight
     */
    public static List<Check> setup(@Nullable PvpArena arena, PvpConfig config, boolean behindProxy,
                                    boolean bridgeKeepsTags) {
        List<Check> checks = new ArrayList<>();
        if (arena == null) {
            checks.add(Check.info(TITLE, "not set up (optional). Select it with the setup wand, then:",
                    "/pvp setregion"));
            return checks;
        }
        if (!config.enabled()) {
            checks.add(Check.info(TITLE, "set, but off in pvp.yml (enabled: false)", null));
            return checks;
        }
        if (arena.kit().isEmpty()) {
            checks.add(Check.warn(TITLE, "the kit is empty, so players fight with their fists. Put the kit in your"
                    + " inventory (armor on) and run:", "/pvp kit"));
        } else if (arena.spawn() == null) {
            checks.add(Check.info(TITLE, "no spawn yet: players come back in the middle of the area. Stand where they"
                    + " should and run:", "/pvp setspawn"));
        } else {
            checks.add(Check.ok(TITLE, "set, " + arena.kit().size() + " kit items"));
        }
        if (behindProxy && config.combatTagMillis() > 0 && !bridgeKeepsTags) {
            checks.add(Check.warn("PvP combat tag", "lobby-bridge on the proxy is older than 0.6, so players can"
                    + " still use /server during a fight. Update lobby-bridge.jar.", null));
        }
        return checks;
    }
}