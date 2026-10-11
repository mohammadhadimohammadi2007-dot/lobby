package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;

import net.minestom.server.entity.Player;

import java.util.List;

/** Live server facts shown by {@code /setup-lobby info}. */
public interface ServerInfo {

    /** Connection mode in words, e.g. "velocity (modern forwarding)". */
    String modeDescription();

    /** Ticks per second. */
    double tps();

    /** Milliseconds per tick. */
    double mspt();

    /** The loaded map. */
    LobbyWorld world();

    /** State of every optional integration. */
    List<IntegrationStatus> integrations();

    /** Bridge state in words, e.g. "connected, last update 1s ago". */
    String bridgeStatus();

    /**
     * Whether the group and server names used in menus, NPCs, portals... are known to the proxy, in words,
     * for {@code /setup-lobby info}.
     */
    default String networkCheck() {
        return "not checked";
    }

    /**
     * How busy the two display threads (holograms and NPCs; scoreboard, tab list, nametags and bars) were
     * over the last minute, in words.
     */
    default String displayLoad() {
        return "not running";
    }

    /**
     * The client version the lobby uses for {@code player}, in words, e.g. "1.8.x (protocol 47), legacy,
     * reported by the proxy".
     */
    default String clientOf(Player player) {
        return "unknown";
    }

    /** What {@code /setup-lobby setup} and {@code doctor} look at. Tick thread. */
    default @org.jetbrains.annotations.Nullable io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.SetupFacts setupFacts() {
        return null;
    }

    /** The setup wand, or {@code null} where there is none (tests). */
    default io.github.mohammadhadimohammadi2007_dot.lobby.server.setup.@org.jetbrains.annotations.Nullable SetupWand wand() {
        return null;
    }

    /** The setup checks, with those features added. */
    default io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.SetupChecks setupChecks() {
        return new io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.SetupChecks();
    }
}
