package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.YamlSectionEditor;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbyInstances;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * {@code /lobby <number> | reload | setspawn | info}. Each sub command has its own permission
 * ({@code lobby.command.lobby}, {@code lobby.command.reload}, {@code lobby.command.setspawn},
 * {@code lobby.command.info}).
 */
public final class LobbyCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyCommand.class);
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final ConfigManager configManager;
    private final LobbyText text;

    public LobbyCommand(ConfigManager configManager, LobbyText text, PermissionService permissions,
                        ServerInfo serverInfo, LobbyInstances lobbies) {
        super("lobby");
        this.configManager = configManager;
        this.text = text;
        setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_LOBBY,
                Permissions.COMMAND_RELOAD, Permissions.COMMAND_SETSPAWN, Permissions.COMMAND_INFO));
        setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.LOBBY_USAGE, sender)));

        // /lobby <number> moves the player to another lobby instance of this server.
        var number = ArgumentType.Integer("number").min(1).max(LobbyConfig.MAX_INSTANCES);
        addConditionalSyntax(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_LOBBY),
                (sender, context) -> switchLobby(sender, lobbies, context.get(number)), number);

        Command reload = new Command("reload");
        reload.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_RELOAD));
        reload.setDefaultExecutor((sender, context) -> reload(sender));
        addSubcommand(reload);

        Command setSpawn = new Command("setspawn");
        setSpawn.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_SETSPAWN));
        setSpawn.setDefaultExecutor((sender, context) -> setSpawn(sender));
        addSubcommand(setSpawn);

        Command info = new Command("info");
        info.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_INFO));
        info.setDefaultExecutor((sender, context) -> sender.sendMessage(infoMessage(sender, serverInfo)));
        addSubcommand(info);
    }

    private void switchLobby(CommandSender sender, LobbyInstances lobbies, int wanted) {
        if (sender instanceof Player player) {
            lobbies.switchTo(player, wanted);
        } else {
            sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
        }
    }

    /** Reloads on a background thread (it reads files) and reports back. */
    private void reload(CommandSender sender) {
        Async.supply(this::reloadOrThrow).whenComplete((result, error) -> Async.onTickThread(() -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                LOGGER.error("Reload failed: {}", cause.getMessage());
                sender.sendMessage(text.message(MessageKey.RELOAD_FAILED, sender, Messages.text("error", cause.getMessage())));
                return;
            }
            reportReload(sender, result);
        }));
    }

    private ConfigManager.ReloadResult reloadOrThrow() {
        try {
            return configManager.reload();
        } catch (ConfigException e) {
            throw new CompletionException(e);
        }
    }

    private void reportReload(CommandSender sender, ConfigManager.ReloadResult result) {
        sender.sendMessage(text.message(MessageKey.RELOAD_DONE, sender));
        if (!result.restartNeeded().isEmpty()) {
            String options = String.join(", ", result.restartNeeded());
            LOGGER.warn("These changes need a restart: {}", options);
            sender.sendMessage(text.message(MessageKey.RELOAD_RESTART_NEEDED, sender, Messages.text("options", options)));
        }
        if (!result.snapshot().warnings().isEmpty()) {
            sender.sendMessage(text.message(MessageKey.RELOAD_WARNINGS, sender,
                    Messages.text("warnings", result.snapshot().warnings().size())));
        }
    }

    /** Saves the player's position as the spawn in config.yml (keeping comments), then reloads. */
    private void setSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
            return;
        }
        Pos position = player.getPosition();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("x", format(position.x(), 2));
        values.put("y", format(position.y(), 2));
        values.put("z", format(position.z(), 2));
        values.put("yaw", format(position.yaw(), 1));
        values.put("pitch", format(position.pitch(), 1));

        Async.supply(() -> {
            try {
                YamlSectionEditor.update(configManager.dataDir().resolve(ConfigManager.CONFIG_FILE), "spawn", values);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
            return reloadOrThrow();
        }).whenComplete((result, error) -> Async.onTickThread(() -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                MessageKey key = cause instanceof ConfigException ? MessageKey.RELOAD_FAILED : MessageKey.SETSPAWN_FAILED;
                LOGGER.error("Setting spawn failed: {}", cause.getMessage());
                player.sendMessage(text.message(key, player, Messages.text("error", cause.getMessage())));
                return;
            }
            LOGGER.info("{} set the spawn to {}", player.getUsername(), values);
            player.sendMessage(text.message(MessageKey.SETSPAWN_DONE, player,
                    Messages.text("x", values.get("x")), Messages.text("y", values.get("y")),
                    Messages.text("z", values.get("z")), Messages.text("yaw", values.get("yaw")),
                    Messages.text("pitch", values.get("pitch"))));
        }));
    }

    private Component infoMessage(CommandSender sender, ServerInfo info) {
        ConfigSnapshot snapshot = configManager.current();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB;
        long maxMb = runtime.maxMemory() / BYTES_PER_MB;
        String integrations = info.integrations().stream()
                .filter(status -> status.state() != IntegrationStatus.State.DISABLED)
                .map(status -> status.state() == IntegrationStatus.State.ACTIVE ? status.name() : status.name() + " (failed)")
                .collect(Collectors.joining(", "));
        return text.message(MessageKey.INFO, sender,
                Messages.text("mode", info.modeDescription()),
                Messages.text("players", MinecraftServer.getConnectionManager().getOnlinePlayerCount()),
                Messages.text("max-players", snapshot.config().server().maxPlayers()),
                Messages.text("tps", format(info.tps(), 1)),
                Messages.text("mspt", format(info.mspt(), 2)),
                Messages.text("memory-used", usedMb),
                Messages.text("memory-max", maxMb),
                Messages.text("world", info.world().name()),
                Messages.text("world-format", info.world().format().name().toLowerCase(Locale.ROOT)),
                Messages.text("chunks", info.world().chunkCount()),
                Messages.text("integrations", integrations.isEmpty() ? "none" : integrations),
                Messages.text("bridge", info.bridgeStatus()),
                Messages.text("display", info.displayLoad()),
                Messages.text("network", info.networkCheck()),
                Messages.text("client", sender instanceof Player player ? info.clientOf(player) : "console"));
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
