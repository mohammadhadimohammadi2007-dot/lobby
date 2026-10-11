package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.TickStats;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.number.ArgumentDouble;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The vanilla-style commands of the lobby: {@code list}, {@code say}, {@code tp}, {@code gamemode},
 * {@code clear}, {@code version} and {@code tps}. Every one works from the console; where a player is
 * needed, the console names one. Kick, ban and whitelist belong to the proxy.
 */
public final class VanillaCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaCommands.class);
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final LobbyText text;
    private final PermissionService permissions;
    private final TickStats ticks;
    private final String version;
    private final Consumer<Player> afterClear;

    /**
     * @param version    this lobby's version, for {@code /version}
     * @param afterClear gives a cleared player their hotbar items back
     */
    public VanillaCommands(LobbyText text, PermissionService permissions, TickStats ticks, String version,
                           Consumer<Player> afterClear) {
        this.text = text;
        this.permissions = permissions;
        this.ticks = ticks;
        this.version = version;
        this.afterClear = afterClear;
    }

    /** The commands to register. */
    public List<Command> commands() {
        return List.of(list(), say(), tp(), gamemode(), clear(), version(), tps());
    }

    private Command list() {
        Command command = new Command("list");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_LIST));
        command.setDefaultExecutor((sender, context) -> {
            Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
            String names = online.stream().map(Player::getUsername).sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));
            sender.sendMessage(text.message(MessageKey.LIST, sender, Messages.text("count", online.size()),
                    Messages.text("names", names.isEmpty() ? "-" : names)));
        });
        return command;
    }

    private Command say() {
        Command command = new Command("say");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_SAY));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.SAY_USAGE, sender)));
        ArgumentStringArray words = ArgumentType.StringArray("message");
        command.addSyntax((sender, context) -> {
            String message = String.join(" ", context.get(words)).strip();
            // Plain text, like vanilla: no colors or links from a typed message.
            var line = text.message(MessageKey.SAY, (Player) null, Messages.text("sender", senderName(sender)),
                    Messages.text("message", message));
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> player.sendMessage(line));
            if (!(sender instanceof Player)) {
                sender.sendMessage(line);
            }
        }, words);
        return command;
    }

    private Command tp() {
        Command command = new Command("tp", "teleport");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_TP));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.TP_USAGE, sender)));
        ArgumentWord target = onlinePlayer(ArgumentType.Word("target"));
        ArgumentWord who = onlinePlayer(ArgumentType.Word("who"));
        ArgumentDouble x = ArgumentType.Double("x");
        ArgumentDouble y = ArgumentType.Double("y");
        ArgumentDouble z = ArgumentType.Double("z");
        // /tp <target>: yourself to a player.
        command.addSyntax((sender, context) -> {
            Player self = self(sender);
            Player to = online(sender, context.get(target));
            if (self != null && to != null) {
                teleport(sender, self, to.getPosition(), to);
            }
        }, target);
        // /tp <who> <target>: a player to a player (works from the console).
        command.addSyntax((sender, context) -> {
            Player moved = online(sender, context.get(who));
            Player to = moved == null ? null : online(sender, context.get(target));
            if (to != null) {
                teleport(sender, moved, to.getPosition(), to);
            }
        }, who, target);
        // /tp <x> <y> <z>: yourself to a place.
        command.addSyntax((sender, context) -> {
            Player self = self(sender);
            if (self != null) {
                teleport(sender, self, new Pos(context.get(x), context.get(y), context.get(z), self.getPosition().yaw(),
                        self.getPosition().pitch()), null);
            }
        }, x, y, z);
        // /tp <who> <x> <y> <z>: a player to a place (works from the console).
        command.addSyntax((sender, context) -> {
            Player moved = online(sender, context.get(who));
            if (moved != null) {
                teleport(sender, moved, new Pos(context.get(x), context.get(y), context.get(z),
                        moved.getPosition().yaw(), moved.getPosition().pitch()), null);
            }
        }, who, x, y, z);
        return command;
    }

    /** Moves {@code moved}; into {@code to}'s lobby instance first if they are in another one. */
    private void teleport(CommandSender sender, Player moved, Pos where, @Nullable Player to) {
        if (to != null && to.getInstance() != null && to.getInstance() != moved.getInstance()) {
            moved.setInstance(to.getInstance(), where);
        } else {
            moved.teleport(where);
        }
        String place = to != null ? to.getUsername()
                : String.format(Locale.ROOT, "%.1f %.1f %.1f", where.x(), where.y(), where.z());
        sender.sendMessage(text.message(MessageKey.TP_DONE, sender, Messages.text("player", moved.getUsername()),
                Messages.text("target", place)));
    }

    private Command gamemode() {
        Command command = new Command("gamemode", "gm");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_GAMEMODE));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.GAMEMODE_USAGE, sender)));
        ArgumentWord mode = ArgumentType.Word("mode").from("survival", "creative", "adventure", "spectator",
                "s", "c", "a", "sp", "0", "1", "2", "3");
        ArgumentWord who = onlinePlayer(ArgumentType.Word("who"));
        command.addSyntax((sender, context) -> {
            Player self = self(sender);
            if (self != null) {
                gamemode(sender, self, context.get(mode));
            }
        }, mode);
        command.addSyntax((sender, context) -> {
            Player player = online(sender, context.get(who));
            if (player != null) {
                gamemode(sender, player, context.get(mode));
            }
        }, mode, who);
        return command;
    }

    private void gamemode(CommandSender sender, Player player, String name) {
        GameMode mode = switch (name.toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            default -> GameMode.SPECTATOR;
        };
        player.setGameMode(mode);
        LOGGER.info("{} set the game mode of {} to {}", CommandSupport.name(sender), player.getUsername(), mode);
        sender.sendMessage(text.message(MessageKey.GAMEMODE_DONE, sender, Messages.text("player", player.getUsername()),
                Messages.text("mode", mode.name().toLowerCase(Locale.ROOT))));
    }

    private Command clear() {
        Command command = new Command("clear");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_CLEAR));
        command.setDefaultExecutor((sender, context) -> {
            Player self = self(sender);
            if (self != null) {
                clear(sender, self);
            }
        });
        ArgumentWord who = onlinePlayer(ArgumentType.Word("who"));
        command.addSyntax((sender, context) -> {
            Player player = online(sender, context.get(who));
            if (player != null) {
                clear(sender, player);
            }
        }, who);
        return command;
    }

    private void clear(CommandSender sender, Player player) {
        player.getInventory().clear();
        // The lobby's own hotbar items always come back.
        afterClear.accept(player);
        sender.sendMessage(text.message(MessageKey.CLEAR_DONE, sender, Messages.text("player", player.getUsername())));
    }

    private Command version() {
        Command command = new Command("version", "ver");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_VERSION));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(text.message(MessageKey.VERSION, sender,
                Messages.text("version", version),
                Messages.text("minecraft", MinecraftServer.VERSION_NAME),
                Messages.text("protocol", MinecraftServer.PROTOCOL_VERSION),
                Messages.text("java", Runtime.version().feature()))));
        return command;
    }

    private Command tps() {
        Command command = new Command("tps");
        command.setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_TPS));
        command.setDefaultExecutor((sender, context) -> {
            Runtime runtime = Runtime.getRuntime();
            sender.sendMessage(text.message(MessageKey.TPS, sender,
                    Messages.text("tps", String.format(Locale.ROOT, "%.1f", ticks.tps())),
                    Messages.text("mspt", String.format(Locale.ROOT, "%.2f", ticks.mspt())),
                    Messages.text("memory-used", (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB),
                    Messages.text("memory-max", runtime.maxMemory() / BYTES_PER_MB)));
        });
        return command;
    }

    /** The sender as a player, or {@code null} after telling the console to name one. */
    private @Nullable Player self(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(text.message(MessageKey.NAME_A_PLAYER, sender));
        return null;
    }

    private @Nullable Player online(CommandSender sender, String name) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name);
        if (player == null) {
            sender.sendMessage(text.message(MessageKey.PLAYER_NOT_ONLINE, sender, Messages.text("player", name)));
        }
        return player;
    }

    private static String senderName(CommandSender sender) {
        return sender instanceof Player player ? player.getUsername() : "Server";
    }

    private static ArgumentWord onlinePlayer(ArgumentWord argument) {
        argument.setSuggestionCallback((sender, context, suggestion) -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                suggestion.addEntry(new SuggestionEntry(player.getUsername()));
            }
        });
        return argument;
    }
}
