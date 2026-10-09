package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import java.util.Set;

/** Every permission node the lobby checks. Listed in the README for server owners. */
public final class Permissions {

    public static final String COMMAND_SPAWN = "lobby.command.spawn";
    public static final String COMMAND_RELOAD = "lobby.command.reload";
    public static final String COMMAND_SETSPAWN = "lobby.command.setspawn";
    public static final String COMMAND_INFO = "lobby.command.info";
    /** Use {@code /lobby <number>} and the lobby selector to move between the lobbies of this server. */
    public static final String COMMAND_LOBBY = "lobby.command.lobby";
    /** Create, edit and delete NPCs with {@code /npc}. */
    public static final String COMMAND_NPC = "lobby.command.npc";
    /** Create, edit and delete holograms with {@code /hologram}. */
    public static final String COMMAND_HOLOGRAM = "lobby.command.hologram";
    /** Join a lobby instance that already holds {@code lobbies.players-per-instance} players. */
    public static final String LOBBY_JOIN_FULL = "lobby.lobbies.join-full";
    /** Ignore the protection settings in config.yml (break and place blocks, drop items...). */
    public static final String BYPASS_PROTECTION = "lobby.bypass.protection";

    /**
     * Permissions every player has when LuckPerms is disabled. With LuckPerms, give these to your
     * default group instead.
     */
    public static final Set<String> EVERYONE = Set.of(COMMAND_SPAWN, COMMAND_LOBBY,
            "lobby.command.chat", "lobby.command.ch", "lobby.command.ignore", "lobby.command.msg", "lobby.chat.emoji");

    private Permissions() {
    }
}
