package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.setup.SetupWand;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@code /pvp setregion | setspawn | kit | info | delete}: sets up the PvP area. Permission
 * {@code lobby.command.pvp}. See docs/pvp.md.
 */
public final class PvpCommand extends Command {

    private final PvpService pvp;
    private final LobbyText text;
    private final SetupWand wand;

    public PvpCommand(PvpService pvp, LobbyText text, PermissionService permissions, SetupWand wand) {
        super("pvp");
        this.pvp = pvp;
        this.text = text;
        this.wand = wand;
        setCondition((sender, commandString) -> {
            boolean allowed = permissions.hasPermission(sender, Permissions.COMMAND_PVP);
            if (!allowed && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return allowed;
        });
        setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.PVP_USAGE, sender)));
        addSubcommand(playerOnly("setregion", this::setRegion));
        addSubcommand(playerOnly("setspawn", player -> {
            if (pvp.arena() == null) {
                player.sendMessage(text.message(MessageKey.PVP_NO_AREA, player));
            } else if (pvp.setSpawn(player.getPosition())) {
                player.sendMessage(text.message(MessageKey.PVP_SPAWN_SET, player));
            } else {
                player.sendMessage(text.message(MessageKey.PVP_SPAWN_OUTSIDE, player));
            }
        }));
        addSubcommand(playerOnly("kit", player -> {
            Kit kit = Kit.of(player);
            if (pvp.setKit(kit)) {
                player.sendMessage(text.message(MessageKey.PVP_KIT_SAVED, player, Messages.text("items", kit.size())));
            } else {
                player.sendMessage(text.message(MessageKey.PVP_NO_AREA, player));
            }
        }));
        Command info = new Command("info");
        info.setDefaultExecutor((sender, context) -> info(sender));
        addSubcommand(info);
        Command delete = new Command("delete", "remove");
        delete.setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(
                pvp.delete() ? MessageKey.PVP_DELETED : MessageKey.PVP_NO_AREA, sender)));
        addSubcommand(delete);
    }

    private void setRegion(Player player) {
        Optional<Region> selection = wand.region(player, PvpArena.OWNER, PvpArena.NAME);
        if (selection.isEmpty()) {
            player.sendMessage(text.message(MessageKey.PVP_NO_SELECTION, player));
            return;
        }
        Region region = selection.get();
        if (region.volume() > PvpArena.MAX_VOLUME) {
            player.sendMessage(text.message(MessageKey.PVP_TOO_BIG, player, Messages.text("blocks", region.volume()),
                    Messages.text("max", PvpArena.MAX_VOLUME)));
            return;
        }
        pvp.setRegion(region);
        player.sendMessage(text.message(MessageKey.PVP_REGION_SET, player, Messages.text("blocks", region.volume())));
    }

    private void info(CommandSender sender) {
        PvpArena arena = pvp.arena();
        if (arena == null) {
            sender.sendMessage(text.message(MessageKey.PVP_NO_AREA, sender));
            return;
        }
        Region region = arena.region();
        Pos spawn = arena.respawn();
        PvpConfig config = pvp.config();
        String mode = (config.enabled() ? "" : "off in pvp.yml, ") + "by the attacker's version"
                + (config.sameVersionOnly() ? ", same version only" : "");
        sender.sendMessage(text.message(MessageKey.PVP_INFO, sender,
                Messages.text("size", (region.maxX() - region.minX() + 1) + "x" + (region.maxY() - region.minY() + 1)
                        + "x" + (region.maxZ() - region.minZ() + 1)),
                Messages.text("spawn", String.format(Locale.ROOT, "%.1f %.1f %.1f%s", spawn.x(), spawn.y(), spawn.z(),
                        arena.spawn() == null ? " (middle, not set)" : "")),
                Messages.text("items", arena.kit().size()),
                Messages.text("players", pvp.playersInArea()),
                Messages.text("mode", mode)));
    }

    private Command playerOnly(String name, Consumer<Player> action) {
        Command command = new Command(name);
        command.setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                action.accept(player);
            } else {
                sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
            }
        });
        return command;
    }
}