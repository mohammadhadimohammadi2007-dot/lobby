package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import java.util.Set;

/** Every permission node the lobby checks. Listed in the README for server owners. */
public final class Permissions {

    public static final String COMMAND_SPAWN = "lobby.command.spawn";
    public static final String COMMAND_RELOAD = "lobby.command.reload";
    public static final String COMMAND_SETSPAWN = "lobby.command.setspawn";
    public static final String COMMAND_INFO = "lobby.command.info";
    /** {@code /setup-lobby setup} and {@code /setup-lobby doctor}. */
    public static final String COMMAND_SETUP = "lobby.command.setup";
    /** {@code /op}, {@code /deop} and {@code /ops}. Operators have it like every permission. */
    public static final String COMMAND_OP = "lobby.command.op";
    /** {@code /list}: who is online. Everyone has it (players see it if commands.yml lists it). */
    public static final String COMMAND_LIST = "lobby.command.list";
    public static final String COMMAND_SAY = "lobby.command.say";
    public static final String COMMAND_TP = "lobby.command.tp";
    public static final String COMMAND_GAMEMODE = "lobby.command.gamemode";
    public static final String COMMAND_CLEAR = "lobby.command.clear";
    public static final String COMMAND_VERSION = "lobby.command.version";
    public static final String COMMAND_TPS = "lobby.command.tps";
    /** {@code /fly <player>}: switch flying for someone else. */
    public static final String COMMAND_FLY_OTHERS = "lobby.command.fly.others";
    /** {@code /stop}. */
    public static final String COMMAND_STOP = "lobby.command.stop";
    /** Use {@code /lobbies [number]} and the lobby selector to move between the lobbies of this server. */
    public static final String COMMAND_LOBBIES = "lobby.command.lobbies";
    /** Create, edit and delete NPCs with {@code /npc}. */
    public static final String COMMAND_NPC = "lobby.command.npc";
    /** Create, edit and delete holograms with {@code /hologram}. */
    public static final String COMMAND_HOLOGRAM = "lobby.command.hologram";
    /** Create, edit and delete portals with {@code /portal}. */
    public static final String COMMAND_PORTAL = "lobby.command.portal";
    public static final String COMMAND_PVP = "lobby.command.pvp";
    /** Join a lobby instance that already holds {@code lobbies.players-per-instance} players. */
    public static final String LOBBY_JOIN_FULL = "lobby.lobbies.join-full";
    /** Ignore the protection settings in config.yml (break and place blocks, drop items...). */
    public static final String BYPASS_PROTECTION = "lobby.bypass.protection";

    /**
     * Permissions every player has when LuckPerms is disabled. With LuckPerms, give these to your
     * default group instead.
     */
    public static final Set<String> EVERYONE = Set.of(COMMAND_SPAWN, COMMAND_LOBBIES, COMMAND_LIST,
            "lobby.command.chat", "lobby.command.ignore", "lobby.chat.emoji");

    private Permissions() {
    }
}
