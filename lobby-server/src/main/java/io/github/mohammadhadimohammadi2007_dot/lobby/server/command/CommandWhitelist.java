package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.condition.CommandCondition;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.trait.PlayerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Which commands a player sees in tab completion and may run, from {@code commands.yml}.
 *
 * <p>A player is in {@code default} and in every listed group they belong to: through LuckPerms
 * ({@code group.<name>}, so inherited groups count) or the permission {@code lobby.commands.group.<name>}.
 * For each command, the group with the highest weight that mentions it decides: {@code name} allows it,
 * {@code -name} hides it, {@code *} allows everything that group does not hide. A command allowed here
 * still needs its own permission.
 *
 * <p>A hidden command is left out of the command tree the client gets, and typing it gives the same answer
 * as a command that does not exist. Operators, the console and {@code lobby.commands.bypass} see
 * everything. Commands run by actions (NPCs, menus, hotbar items) are not checked, so a command can be
 * hidden from typing and still be used from a menu.
 */
public final class CommandWhitelist {

    /** See every command, as without commands.yml. */
    public static final String BYPASS = "lobby.commands.bypass";
    /** Membership of a commands.yml group without LuckPerms. */
    public static final String GROUP_PREFIX = "lobby.commands.group.";

    private static final ThreadLocal<Boolean> FROM_ACTION = ThreadLocal.withInitial(() -> false);

    private final Supplier<CommandsConfig> config;
    private final PermissionService permissions;
    private final LobbyText text;
    private final CommandManager commands;

    public CommandWhitelist(Supplier<CommandsConfig> config, PermissionService permissions, LobbyText text,
                            CommandManager commands) {
        this.config = config;
        this.permissions = permissions;
        this.text = text;
        this.commands = commands;
    }

    /**
     * Applies the whitelist to every registered command, answers unknown commands, and checks typed
     * commands. Call after the commands are registered; later ones go through {@link #guard(Command)}.
     */
    public void register(EventNode<PlayerEvent> node) {
        commands.getCommands().forEach(this::guard);
        commands.setUnknownCommandCallback((sender, command) -> unknown(sender));
        node.addListener(PlayerCommandEvent.class, event -> {
            if (FROM_ACTION.get()) {
                return;
            }
            String root = event.getCommand().strip().split(" ", 2)[0].toLowerCase(Locale.ROOT);
            Command command = commands.getCommand(root);
            if (command != null && !allowed(event.getPlayer(), command)) {
                event.setCancelled(true);
                unknown(event.getPlayer());
            }
        });
    }

    /** Hides {@code command} from the command tree of players whose groups do not allow it. */
    public void guard(Command command) {
        CommandCondition original = command.getCondition();
        command.setCondition((sender, commandString) -> {
            // Only the tree (commandString null) is decided here; typed commands were checked in the event.
            if (commandString == null && sender instanceof Player player && !allowed(player, command)) {
                return false;
            }
            return original == null || original.canUse(sender, commandString);
        });
    }

    /** Runs {@code task} (a command an action runs for a player) without the whitelist. Tick thread. */
    public static void runFromAction(Runnable task) {
        FROM_ACTION.set(true);
        try {
            task.run();
        } finally {
            FROM_ACTION.set(false);
        }
    }

    /** Sends every online player a fresh command tree, e.g. after commands.yml changed. Tick thread. */
    public void refreshAll() {
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(Player::refreshCommands);
    }

    /** True if {@code player} may see and use {@code command} (its own permission aside). */
    public boolean allowed(Player player, Command command) {
        CommandsConfig current = config.get();
        if (!current.enabled() || permissions.hasPermission(player, BYPASS)) {
            return true;
        }
        List<String> names = new ArrayList<>();
        names.add(command.getName().toLowerCase(Locale.ROOT));
        for (String alias : command.getAliases()) {
            names.add(alias.toLowerCase(Locale.ROOT));
        }
        for (CommandsConfig.Group group : groupsOf(player, current)) {
            for (String name : names) {
                if (group.denied().contains(name)) {
                    return false;
                }
            }
            for (String name : names) {
                if (group.allowed().contains(name)) {
                    return true;
                }
            }
            if (group.allowed().contains(CommandsConfig.ALL)) {
                return true;
            }
        }
        return false;
    }

    /** The listed groups {@code player} is in, highest weight first. */
    List<CommandsConfig.Group> groupsOf(Player player, CommandsConfig current) {
        List<CommandsConfig.Group> groups = new ArrayList<>();
        for (CommandsConfig.Group group : current.groups().values()) {
            if (group.name().equals(CommandsConfig.DEFAULT_GROUP)
                    || permissions.hasPermission(player, "group." + group.name())
                    || permissions.hasPermission(player, GROUP_PREFIX + group.name())) {
                groups.add(group);
            }
        }
        groups.sort(Comparator.comparingInt(this::weight).reversed());
        return groups;
    }

    private int weight(CommandsConfig.Group group) {
        var fromPlugin = permissions.groupWeight(group.name());
        if (fromPlugin.isPresent()) {
            return fromPlugin.getAsInt();
        }
        if (group.weight().isPresent()) {
            return group.weight().getAsInt();
        }
        // default is the floor every other group builds on.
        return group.name().equals(CommandsConfig.DEFAULT_GROUP) ? Integer.MIN_VALUE : 0;
    }

    private void unknown(CommandSender sender) {
        sender.sendMessage(text.message(MessageKey.UNKNOWN_COMMAND, sender));
    }
}
