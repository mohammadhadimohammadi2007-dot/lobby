package io.github.mohammadhadimohammadi2007_dot.lobby.api;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.ActionTypes;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.command.AddonCommands;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.config.AddonConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.AddonEvents;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.hologram.Holograms;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.Menus;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.npc.Npcs;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder.Placeholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.scheduler.AddonScheduler;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Clients;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Cosmetics;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Network;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.PlayerSettings;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Pvp;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Regions;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.ServiceRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Texts;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Vanish;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Everything an addon can use, given to each of its {@link LobbyAddon} methods. One context per addon:
 * whatever is registered through it belongs to that addon and is removed when it is disabled.
 *
 * <p>Available in {@link LobbyAddon#onLoad}: {@link #description}, {@link #logger}, {@link #dataFolder},
 * {@link #config}, {@link #placeholders}, {@link #actionTypes}, {@link #scheduler} and {@link #services}. The rest throws
 * {@link IllegalStateException} until {@link LobbyAddon#onEnable}.
 *
 * <p>Threading: unless a method says otherwise it may be called from any thread. Event listeners run on the
 * thread the event happens on (each event says which); the tick thread must never block, so do slow work
 * (files, databases, web requests) with {@link AddonScheduler#async}.
 */
@NonExtendable
public interface AddonContext {

    /** What {@code addon.json} says. */
    AddonDescription description();

    /** A logger named after the addon. */
    Logger logger();

    /** The addon's own folder, {@code addons/<id>/}, created on first use. */
    Path dataFolder();

    /**
     * {@code addons/<id>/<fileName>}, read as YAML. If the file does not exist yet, the file of the same name
     * inside the addon's jar is copied there first (if the jar has one).
     */
    AddonConfig config(String fileName);

    /** {@code config("config.yml")}. */
    default AddonConfig config() {
        return config("config.yml");
    }

    /** Placeholder namespaces such as {@code %myaddon_value%}. Register them in {@link LobbyAddon#onLoad}. */
    Placeholders placeholders();

    /** Action types for NPCs, holograms, menus, portals and hotbar items. Register them in {@link LobbyAddon#onLoad}. */
    ActionTypes actionTypes();

    /** Commands. */
    AddonCommands commands();

    /** Lobby events such as NPC clicks and chat, and Minestom's own events. */
    AddonEvents events();

    /** Work off the tick thread, on the next tick, later or repeating; cancelled when the addon stops. */
    AddonScheduler scheduler();

    /** Menus: open the ones of menus.yml, or add menus built in code. */
    Menus menus();

    /** Holograms: create, change and remove. */
    Holograms holograms();

    /** NPCs: create, change and remove. */
    Npcs npcs();

    /** Permission checks and rank information (LuckPerms, or the operators list without it). */
    Permissions permissions();

    /** Small values saved per player for this addon, in the lobby's database or in files. */
    PlayerSettings playerSettings();

    /** Servers, groups and player counts reported by the proxy bridge, and sending players to servers. */
    Network network();

    /** Which Minecraft version each player's client runs. */
    Clients clients();

    /** The PvP area: who is in it, who is in combat, and players' records. */
    Pvp pvp();

    /** Trails, hats and gadgets: what players use, and putting cosmetics on or off. */
    Cosmetics cosmetics();

    /** Vanished staff: who is vanished, who sees them, and vanishing players. */
    Vanish vanish();

    /**
     * Services addons offer each other (an economy, stats...). Usable from {@link LobbyAddon#onLoad} on: a service
     * provided there can be used by the addons that depend on it, which are loaded after.
     */
    ServiceRegistry services();

    /** Messages in the lobby's style: MiniMessage, placeholders and the {@code <prefix>} of messages.yml. */
    Texts texts();

    /** Areas of the map: which ones a player is in, and the addon's own, which fire region events too. */
    Regions regions();
}
