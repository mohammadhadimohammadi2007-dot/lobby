package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;

import java.util.List;

/** Live server facts shown by {@code /lobby info}. */
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
}
