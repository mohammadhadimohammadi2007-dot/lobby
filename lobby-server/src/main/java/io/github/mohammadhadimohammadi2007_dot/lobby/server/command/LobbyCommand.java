package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.YamlSectionEditor;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
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
 * {@code /lobby reload | setspawn | info}. Each sub command has its own permission
 * ({@code lobby.command.reload}, {@code lobby.command.setspawn}, {@code lobby.command.info}).
 */
public final class LobbyCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyCommand.class);
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final ConfigManager configManager;

    public LobbyCommand(ConfigManager configManager, PermissionService permissions, ServerInfo serverInfo) {
        super("lobby");
        this.configManager = configManager;
        setCondition(CommandSupport.requireAny(configManager, permissions,
                Permissions.COMMAND_RELOAD, Permissions.COMMAND_SETSPAWN, Permissions.COMMAND_INFO));
        setDefaultExecutor((sender, context) -> sender.sendMessage(messages().render(MessageKey.LOBBY_USAGE)));

        Command reload = new Command("reload");
        reload.setCondition(CommandSupport.requireAny(configManager, permissions, Permissions.COMMAND_RELOAD));
        reload.setDefaultExecutor((sender, context) -> reload(sender));
        addSubcommand(reload);

        Command setSpawn = new Command("setspawn");
        setSpawn.setCondition(CommandSupport.requireAny(configManager, permissions, Permissions.COMMAND_SETSPAWN));
        setSpawn.setDefaultExecutor((sender, context) -> setSpawn(sender));
        addSubcommand(setSpawn);

        Command info = new Command("info");
        info.setCondition(CommandSupport.requireAny(configManager, permissions, Permissions.COMMAND_INFO));
        info.setDefaultExecutor((sender, context) -> sender.sendMessage(infoMessage(serverInfo)));
        addSubcommand(info);
    }

    private Messages messages() {
        return configManager.current().messages();
    }

    /** Reloads on a background thread (it reads files) and reports back. */
    private void reload(CommandSender sender) {
        Async.supply(this::reloadOrThrow).whenComplete((result, error) -> Async.onTickThread(() -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                LOGGER.error("Reload failed: {}", cause.getMessage());
                sender.sendMessage(messages().render(MessageKey.RELOAD_FAILED, Messages.text("error", cause.getMessage())));
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
        Messages messages = result.snapshot().messages();
        sender.sendMessage(messages.render(MessageKey.RELOAD_DONE));
        if (!result.restartNeeded().isEmpty()) {
            String options = String.join(", ", result.restartNeeded());
            LOGGER.warn("These changes need a restart: {}", options);
            sender.sendMessage(messages.render(MessageKey.RELOAD_RESTART_NEEDED, Messages.text("options", options)));
        }
        if (!result.snapshot().warnings().isEmpty()) {
            sender.sendMessage(messages.render(MessageKey.RELOAD_WARNINGS,
                    Messages.text("warnings", result.snapshot().warnings().size())));
        }
    }

    /** Saves the player's position as the spawn in config.yml (keeping comments), then reloads. */
    private void setSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages().render(MessageKey.PLAYERS_ONLY));
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
                player.sendMessage(messages().render(key, Messages.text("error", cause.getMessage())));
                return;
            }
            LOGGER.info("{} set the spawn to {}", player.getUsername(), values);
            player.sendMessage(result.snapshot().messages().render(MessageKey.SETSPAWN_DONE,
                    Messages.text("x", values.get("x")), Messages.text("y", values.get("y")),
                    Messages.text("z", values.get("z")), Messages.text("yaw", values.get("yaw")),
                    Messages.text("pitch", values.get("pitch"))));
        }));
    }

    private Component infoMessage(ServerInfo info) {
        ConfigSnapshot snapshot = configManager.current();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB;
        long maxMb = runtime.maxMemory() / BYTES_PER_MB;
        String integrations = info.integrations().stream()
                .filter(status -> status.state() != IntegrationStatus.State.DISABLED)
                .map(status -> status.state() == IntegrationStatus.State.ACTIVE ? status.name() : status.name() + " (failed)")
                .collect(Collectors.joining(", "));
        return snapshot.messages().render(MessageKey.INFO,
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
                Messages.text("bridge", info.bridgeStatus()));
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
