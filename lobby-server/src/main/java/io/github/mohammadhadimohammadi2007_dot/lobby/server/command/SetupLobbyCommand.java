package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.YamlSectionEditor;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.SetupFacts;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.CheckPrinter;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.Check;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * {@code /setup-lobby setup | doctor | reload | setspawn | info}, the admin command. Each sub command has its
 * own permission ({@code lobby.command.setup}, {@code lobby.command.reload}, {@code lobby.command.setspawn},
 * {@code lobby.command.info}).
 *
 * <p>Not called {@code /lobby}: that name belongs to the proxy's hub command on most networks.
 */
public final class SetupLobbyCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupLobbyCommand.class);
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final ConfigManager configManager;
    private final LobbyText text;

    public SetupLobbyCommand(ConfigManager configManager, LobbyText text, PermissionService permissions,
                        ServerInfo serverInfo) {
        super("setup-lobby");
        this.configManager = configManager;
        this.text = text;
        setCondition(CommandSupport.requireAny(text, permissions,
                Permissions.COMMAND_RELOAD, Permissions.COMMAND_SETSPAWN, Permissions.COMMAND_INFO,
                Permissions.COMMAND_SETUP));
        setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.SETUP_LOBBY_USAGE, sender)));

        Command reload = new Command("reload");
        reload.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_RELOAD));
        reload.setDefaultExecutor((sender, context) -> reload(sender));
        addSubcommand(reload);

        Command setSpawn = new Command("setspawn");
        setSpawn.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_SETSPAWN));
        setSpawn.setDefaultExecutor((sender, context) -> setSpawn(sender));
        var x = ArgumentType.Double("x");
        var y = ArgumentType.Double("y");
        var z = ArgumentType.Double("z");
        var yaw = ArgumentType.Float("yaw");
        var pitch = ArgumentType.Float("pitch");
        setSpawn.addSyntax((sender, context) -> setSpawn(sender,
                new Pos(context.get(x), context.get(y), context.get(z))), x, y, z);
        setSpawn.addSyntax((sender, context) -> setSpawn(sender, new Pos(context.get(x), context.get(y), context.get(z),
                context.get(yaw), context.get(pitch))), x, y, z, yaw, pitch);
        addSubcommand(setSpawn);

        Command setup = new Command("setup");
        setup.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_SETUP));
        setup.setDefaultExecutor((sender, context) -> checks(sender, serverInfo, false));
        addSubcommand(setup);

        Command doctor = new Command("doctor");
        doctor.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_SETUP));
        doctor.setDefaultExecutor((sender, context) -> checks(sender, serverInfo, true));
        addSubcommand(doctor);

        Command wandCommand = new Command("wand");
        wandCommand.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_SETUP,
                Permissions.COMMAND_PORTAL));
        wandCommand.setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player && serverInfo.wand() != null) {
                serverInfo.wand().give(player);
            } else {
                sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
            }
        });
        addSubcommand(wandCommand);

        Command info = new Command("info");
        info.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_INFO));
        info.setDefaultExecutor((sender, context) -> sender.sendMessage(infoMessage(sender, serverInfo)));
        addSubcommand(info);
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

    /** Saves the player's position as the spawn; from the console the coordinates must be given. */
    private void setSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text.message(MessageKey.SETSPAWN_CONSOLE, sender));
            return;
        }
        setSpawn(sender, player.getPosition());
    }

    /** Saves {@code position} as the spawn in config.yml (keeping comments), then reloads. */
    private void setSpawn(CommandSender sender, Pos position) {
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
                sender.sendMessage(text.message(key, sender, Messages.text("error", cause.getMessage())));
                return;
            }
            LOGGER.info("{} set the spawn to {}", CommandSupport.name(sender), values);
            sender.sendMessage(text.message(MessageKey.SETSPAWN_DONE, sender,
                    Messages.text("x", values.get("x")), Messages.text("y", values.get("y")),
                    Messages.text("z", values.get("z")), Messages.text("yaw", values.get("yaw")),
                    Messages.text("pitch", values.get("pitch"))));
        }));
    }

    /** {@code /setup-lobby setup} (the checklist) or {@code doctor} (problems only, with fixes). */
    private void checks(CommandSender sender, ServerInfo info, boolean doctor) {
        SetupFacts facts = info.setupFacts();
        if (facts == null) {
            sender.sendMessage(text.message(MessageKey.SETUP_UNAVAILABLE, sender));
            return;
        }
        List<Check> checks = doctor ? info.setupChecks().doctor(facts) : info.setupChecks().setup(facts);
        sender.sendMessage(text.message(doctor ? MessageKey.DOCTOR_HEADER : MessageKey.SETUP_HEADER, sender,
                Messages.text("summary", CheckPrinter.summary(checks))));
        CheckPrinter.print(sender, checks);
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
