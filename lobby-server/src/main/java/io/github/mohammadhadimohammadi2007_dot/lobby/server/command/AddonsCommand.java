package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.AddonChecks;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.AddonInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.AddonManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.CheckPrinter;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * {@code /setup-lobby addons} lists the addons with their version and state; {@code /setup-lobby addons reload
 * <id>} stops one, reads its jar again and starts it. Permission {@code lobby.command.setup}.
 */
public final class AddonsCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddonsCommand.class);

    private final AddonManager addons;

    public AddonsCommand(AddonManager addons, LobbyText text, PermissionService permissions) {
        super("addons");
        this.addons = addons;
        setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_SETUP));
        setDefaultExecutor((sender, context) -> list(sender));

        Command reload = new Command("reload");
        var id = ArgumentType.Word("id");
        id.setSuggestionCallback((sender, context, suggestion) -> addons.infos()
                .forEach(addon -> suggestion.addEntry(new SuggestionEntry(addon.id()))));
        reload.setDefaultExecutor((sender, context) -> sender.sendMessage(
                Component.text("Usage: /setup-lobby addons reload <id>", NamedTextColor.RED)));
        reload.addSyntax((sender, context) -> reload(sender, context.get(id)), id);
        addSubcommand(reload);
    }

    private void list(CommandSender sender) {
        List<AddonInfo> infos = addons.infos();
        sender.sendMessage(Component.text("Addons (" + infos.size() + "):", NamedTextColor.GOLD));
        CheckPrinter.print(sender, AddonChecks.list(infos));
    }

    /** Off the tick thread: the addon's onLoad and onEnable run, and the lobby's files may be read again. */
    private void reload(CommandSender sender, String id) {
        sender.sendMessage(Component.text("Reloading addon " + id + "...", NamedTextColor.GRAY));
        Async.supply(() -> addons.reload(id)).whenComplete((result, error) -> Async.onTickThread(() -> {
            if (error != null) {
                LOGGER.error("Reloading addon {} failed", id, error);
                sender.sendMessage(Component.text("Reloading failed: " + error.getMessage(), NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text(result.message(), result.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }));
    }
}
