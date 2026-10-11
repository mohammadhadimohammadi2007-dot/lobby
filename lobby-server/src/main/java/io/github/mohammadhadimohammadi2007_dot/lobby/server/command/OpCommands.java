package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.MojangProfiles;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Op;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OpMigration;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OpList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * {@code /op <player>}, {@code /op confirm <code>}, {@code /deop <player>} and {@code /ops}. The console
 * and operators may use them ({@code lobby.command.op}); {@code /op confirm} is for an operator who was
 * given a code.
 */
public final class OpCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpCommands.class);

    private final ConfigManager config;
    private final LobbyText text;
    private final PermissionService permissions;
    private final OpList ops;
    private final MojangProfiles mojang;

    public OpCommands(ConfigManager config, LobbyText text, PermissionService permissions, OpList ops,
                      MojangProfiles mojang) {
        this.config = config;
        this.text = text;
        this.permissions = permissions;
        this.ops = ops;
        this.mojang = mojang;
    }

    /** The commands to register. */
    public List<Command> commands() {
        return List.of(op(), deop(), list());
    }

    private Command op() {
        Command command = new Command("op");
        // Visible to operators, and to an operator who still has to confirm with a code.
        command.setCondition((sender, commandString) -> {
            boolean allowed = permissions.hasPermission(sender, Permissions.COMMAND_OP)
                    || (sender instanceof Player player && ops.hasPendingCode(player.getUuid()));
            if (!allowed && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return allowed;
        });
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.OP_USAGE, sender)));

        Command confirm = new Command("confirm");
        ArgumentWord code = ArgumentType.Word("code");
        confirm.setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.OP_USAGE, sender)));
        confirm.addSyntax((sender, context) -> confirm(sender, context.get(code)), code);
        command.addSubcommand(confirm);

        ArgumentWord target = onlineNames(ArgumentType.Word("player"));
        command.addConditionalSyntax((sender, commandString) -> permissions.hasPermission(sender, Permissions.COMMAND_OP),
                (sender, context) -> op(sender, context.get(target)), target);
        return command;
    }

    private Command deop() {
        Command command = new Command("deop");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_OP));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.DEOP_USAGE, sender)));
        ArgumentWord target = ArgumentType.Word("player");
        target.setSuggestionCallback((sender, context, suggestion) ->
                ops.all().forEach(op -> suggestion.addEntry(new SuggestionEntry(op.name()))));
        command.addSyntax((sender, context) -> deop(sender, context.get(target)), target);
        return command;
    }

    private Command list() {
        Command command = new Command("ops");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_OP));
        command.setDefaultExecutor((sender, context) -> {
            List<Op> all = ops.all();
            sender.sendMessage(text.message(MessageKey.OPS_HEADER, sender, Messages.text("count", all.size())));
            for (Op op : all) {
                sender.sendMessage(text.message(MessageKey.OPS_ENTRY, sender, Messages.text("name", op.name()),
                        Messages.text("status", status(op))));
            }
        });
        return command;
    }

    private String status(Op op) {
        if (op.uuid() == null) {
            return "waiting for their first join";
        }
        Player online = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(op.uuid());
        if (online != null) {
            return ops.isOp(online) ? "online" : "online, must confirm with a code";
        }
        if (protects(config.current().config())) {
            return op.confirmed() ? "offline, " + op.ips().size() + " confirmed address(es)" : "offline, must confirm with a code";
        }
        return "offline";
    }

    private void op(CommandSender sender, String name) {
        Player online = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name);
        if (online != null) {
            ops.op(online);
            online.refreshCommands();
            LOGGER.info("{} made {} an operator", CommandSupport.name(sender), online.getUsername());
            sender.sendMessage(text.message(MessageKey.OP_ADDED, sender, Messages.text("player", online.getUsername())));
            online.sendMessage(text.message(MessageKey.OP_NOW, online));
            return;
        }
        LobbyConfig current = config.current().config();
        Async.supply(() -> OpMigration.resolve(current, name, mojang)).whenComplete((resolved, error) -> Async.onTickThread(() -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                sender.sendMessage(text.message(MessageKey.OP_LOOKUP_FAILED, sender, Messages.text("player", name),
                        Messages.text("error", cause.getMessage())));
                return;
            }
            if (resolved.isEmpty()) {
                sender.sendMessage(text.message(MessageKey.OP_NO_ACCOUNT, sender, Messages.text("player", name)));
                return;
            }
            UUID uuid = resolved.get().orElse(null);
            ops.op(name, uuid);
            LOGGER.info("{} made {} an operator ({})", CommandSupport.name(sender), name, uuid == null ? "UUID on first join" : uuid);
            MessageKey key = protects(current) ? MessageKey.OP_ADDED_NEEDS_CODE
                    : uuid == null ? MessageKey.OP_ADDED_PENDING : MessageKey.OP_ADDED;
            sender.sendMessage(text.message(key, sender, Messages.text("player", name)));
        }));
    }

    private void deop(CommandSender sender, String name) {
        Optional<Op> removed = ops.deop(name);
        if (removed.isEmpty()) {
            sender.sendMessage(text.message(MessageKey.DEOP_UNKNOWN, sender, Messages.text("player", name)));
            return;
        }
        LOGGER.info("{} removed {} from the operators", CommandSupport.name(sender), removed.get().name());
        sender.sendMessage(text.message(MessageKey.DEOP_DONE, sender, Messages.text("player", removed.get().name())));
        UUID uuid = removed.get().uuid();
        Player online = uuid == null ? null : MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
        if (online != null) {
            online.refreshCommands();
            online.sendMessage(text.message(MessageKey.DEOP_NOW, online));
        }
    }

    private void confirm(CommandSender sender, String code) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
            return;
        }
        MessageKey key = switch (ops.confirm(player, code)) {
            case CONFIRMED -> MessageKey.OP_CONFIRMED;
            case WRONG_CODE -> MessageKey.OP_CONFIRM_WRONG;
            case NO_CODE -> MessageKey.OP_CONFIRM_NONE;
        };
        player.sendMessage(text.message(key, player));
        player.refreshCommands();
    }

    private static ArgumentWord onlineNames(ArgumentWord argument) {
        argument.setSuggestionCallback((sender, context, suggestion) -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                suggestion.addEntry(new SuggestionEntry(player.getUsername()));
            }
        });
        return argument;
    }

    /** True if operators must confirm with a code in this configuration. */
    static boolean protects(LobbyConfig config) {
        return config.ops().protection() != OpList.Protection.OFF && !config.namesTrusted();
    }
}
