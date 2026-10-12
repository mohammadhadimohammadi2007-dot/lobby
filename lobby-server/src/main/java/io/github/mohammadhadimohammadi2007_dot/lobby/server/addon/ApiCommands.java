package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.command.AddonCommands;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.condition.CommandCondition;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** An addon's commands, guarded by commands.yml and the permission system like the lobby's own. */
final class ApiCommands implements AddonCommands {

    private final AddonContextImpl context;
    private final AddonServices services;
    private final List<Command> commands = new CopyOnWriteArrayList<>();

    ApiCommands(AddonContextImpl context, AddonServices services) {
        this.context = context;
        this.services = services;
    }

    @Override
    public synchronized void register(Command command, @Nullable String permission) {
        for (String name : names(command)) {
            if (services.commands().commandExists(name)) {
                throw new IllegalArgumentException("A command named '" + name + "' already exists");
            }
        }
        if (permission != null && !permission.isBlank()) {
            CommandCondition original = command.getCondition();
            command.setCondition((sender, commandString) -> {
                if (!services.permissions().hasPermission(sender, permission)) {
                    if (commandString != null) {
                        sender.sendMessage(services.text().message(MessageKey.NO_PERMISSION,
                                sender instanceof Player player ? player : null));
                    }
                    return false;
                }
                return original == null || original.canUse(sender, commandString);
            });
        }
        services.whitelist().guard(command);
        services.commands().register(command);
        commands.add(command);
        refreshTrees();
    }

    @Override
    public synchronized void unregister(Command command) {
        if (commands.remove(command)) {
            services.commands().unregister(command);
            refreshTrees();
        }
    }

    synchronized void cleanup() {
        if (commands.isEmpty()) {
            return;
        }
        commands.forEach(services.commands()::unregister);
        commands.clear();
        refreshTrees();
    }

    private static List<String> names(Command command) {
        List<String> names = new java.util.ArrayList<>(List.of(command.getName()));
        names.addAll(List.of(command.getAliases()));
        return names;
    }

    /** Players get a new command tree, so the change shows in tab completion. */
    private static void refreshTrees() {
        Async.onTickThread(() -> MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(Player::refreshCommands));
    }
}
