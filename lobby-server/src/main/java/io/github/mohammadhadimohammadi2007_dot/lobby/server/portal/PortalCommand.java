package io.github.mohammadhadimohammadi2007_dot.lobby.server.portal;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionEntries;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * {@code /portal}: wand, pos1, pos2, create, remove, list, info, teleport, cooldown, permission and action
 * (add, remove, clear, list). Permission {@code lobby.command.portal}. See docs/movement-and-portals.md.
 */
public final class PortalCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortalCommand.class);
    private static final long MILLIS_PER_SECOND = 1000;

    private final PortalService portals;
    private final LobbyText text;

    public PortalCommand(PortalService portals, LobbyText text, PermissionService permissions) {
        super("portal", "portals");
        this.portals = portals;
        this.text = text;
        setCondition((sender, commandString) -> {
            boolean allowed = permissions.hasPermission(sender, Permissions.COMMAND_PORTAL);
            if (!allowed && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return allowed;
        });
        setDefaultExecutor((sender, context) -> usage(sender));
        ArgumentWord portal = portalArgument();
        addSubcommand(playerOnly("wand", player -> {
            player.getInventory().addItemStack(portals.wand(player));
            player.sendMessage(text.message(MessageKey.PORTAL_WAND_GIVEN, player));
        }));
        addSubcommand(playerOnly("pos1", player -> portals.select(player, 0, player.getPosition())));
        addSubcommand(playerOnly("pos2", player -> portals.select(player, 1, player.getPosition())));
        addSubcommand(create());
        addSubcommand(withPortal("remove", portal, (sender, found) -> {
            portals.delete(found.name());
            sender.sendMessage(text.message(MessageKey.PORTAL_DELETED, sender, Messages.text("name", found.name())));
        }, "delete"));
        addSubcommand(list());
        addSubcommand(withPortal("info", portal, this::info));
        addSubcommand(withPortal("teleport", portal, (sender, found) -> {
            if (sender instanceof Player player) {
                Region region = found.region();
                player.teleport(new Pos((region.minX() + region.maxX() + 1) / 2.0, region.minY(),
                        (region.minZ() + region.maxZ() + 1) / 2.0));
            }
        }, "tp"));
        addSubcommand(cooldown(portal));
        addSubcommand(permission(portal));
        addSubcommand(action(portal));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(text.message(MessageKey.PORTAL_USAGE, sender));
    }

    private ArgumentWord portalArgument() {
        ArgumentWord argument = ArgumentType.Word("portal");
        argument.setSuggestionCallback((sender, context, suggestion) -> {
            for (Portal portal : portals.all()) {
                suggestion.addEntry(new SuggestionEntry(portal.name()));
            }
        });
        return argument;
    }

    private Command playerOnly(String name, java.util.function.Consumer<Player> action) {
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

    private Command withPortal(String name, ArgumentWord portal, BiConsumer<CommandSender, Portal> action,
                               String... aliases) {
        Command command = new Command(name, aliases);
        command.setDefaultExecutor((sender, context) -> usage(sender));
        command.addSyntax((sender, context) -> {
            Portal found = found(sender, context.get(portal));
            if (found != null) {
                action.accept(sender, found);
            }
        }, portal);
        return command;
    }

    private @Nullable Portal found(CommandSender sender, String name) {
        Portal portal = portals.get(name);
        if (portal == null) {
            sender.sendMessage(text.message(MessageKey.PORTAL_UNKNOWN, sender, Messages.text("name", name)));
        }
        return portal;
    }

    private Command create() {
        Command command = new Command("create");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        ArgumentWord name = ArgumentType.Word("name");
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
                return;
            }
            String wanted = context.get(name).toLowerCase(Locale.ROOT);
            MessageKey answer = switch (portals.create(player, wanted)) {
                case CREATED -> MessageKey.PORTAL_CREATED;
                case BAD_NAME -> MessageKey.PORTAL_BAD_NAME;
                case EXISTS -> MessageKey.PORTAL_EXISTS;
                case NO_SELECTION -> MessageKey.PORTAL_NO_SELECTION;
                case TOO_BIG -> MessageKey.PORTAL_TOO_BIG;
            };
            player.sendMessage(text.message(answer, player, Messages.text("name", wanted),
                    Messages.text("max", Portal.MAX_VOLUME)));
        }, name);
        return command;
    }

    private Command list() {
        Command command = new Command("list");
        command.setDefaultExecutor((sender, context) -> {
            List<Portal> all = portals.all();
            if (all.isEmpty()) {
                sender.sendMessage(text.message(MessageKey.PORTAL_LIST_EMPTY, sender));
                return;
            }
            sender.sendMessage(text.message(MessageKey.PORTAL_LIST, sender, Messages.text("count", all.size()),
                    Messages.text("names", all.stream().map(Portal::name).collect(Collectors.joining(", ")))));
        });
        return command;
    }

    private void info(CommandSender sender, Portal portal) {
        Region region = portal.region();
        sender.sendMessage(text.message(MessageKey.PORTAL_INFO, sender,
                Messages.text("name", portal.name()),
                Messages.text("from", region.minX() + ", " + region.minY() + ", " + region.minZ()),
                Messages.text("to", region.maxX() + ", " + region.maxY() + ", " + region.maxZ()),
                Messages.text("cooldown", portal.cooldownMillis() + " ms"),
                Messages.text("permission", portal.permission().isEmpty() ? "everyone" : portal.permission()),
                Messages.text("actions", portal.entries().isEmpty() ? "none" : ActionEntries.describeAll(portal.entries()))));
    }

    private Command cooldown(ArgumentWord portal) {
        Command command = new Command("cooldown");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        ArgumentWord time = ArgumentType.Word("time");
        command.addSyntax((sender, context) -> {
            Portal found = found(sender, context.get(portal));
            Long millis = millis(context.get(time));
            if (found == null) {
                return;
            }
            if (millis == null) {
                invalidAction(sender, "'" + context.get(time) + "' is not a time like 2s or 500ms");
                return;
            }
            portals.update(found.withCooldown(millis, warning -> LOGGER.warn("{}", warning)));
            set(sender, found.name(), "cooldown", millis + " ms");
        }, portal, time);
        return command;
    }

    private Command permission(ArgumentWord portal) {
        Command command = new Command("permission");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        ArgumentWord node = ArgumentType.Word("permission");
        command.addSyntax((sender, context) -> {
            Portal found = found(sender, context.get(portal));
            if (found == null) {
                return;
            }
            String value = context.get(node).equalsIgnoreCase("@none") ? "" : context.get(node);
            portals.update(found.withPermission(value));
            set(sender, found.name(), "permission", value.isEmpty() ? "everyone" : value);
        }, portal, node);
        return command;
    }

    /** {@code action <portal> add <action...>}, {@code remove <n>}, {@code clear} and {@code list}. */
    private Command action(ArgumentWord portal) {
        Command command = new Command("action", "actions");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        var line = ArgumentType.StringArray("action");
        command.addSyntax((sender, context) -> editActions(sender, context.get(portal), found -> {
            String written = String.join(" ", context.get(line));
            try {
                ActionParser.parse(written);
            } catch (ActionParser.ActionException e) {
                invalidAction(sender, e.getMessage());
                return null;
            }
            List<Object> entries = new ArrayList<>(found.entries());
            entries.add(written);
            return entries;
        }), portal, ArgumentType.Literal("add"), line);
        var number = ArgumentType.Integer("number").min(1);
        command.addSyntax((sender, context) -> editActions(sender, context.get(portal), found -> {
            int index = context.get(number) - 1;
            if (index >= found.entries().size()) {
                invalidAction(sender, "it has " + found.entries().size() + " action(s)");
                return null;
            }
            List<Object> entries = new ArrayList<>(found.entries());
            entries.remove(index);
            return entries;
        }), portal, ArgumentType.Literal("remove"), number);
        command.addSyntax((sender, context) -> editActions(sender, context.get(portal), found -> List.of()),
                portal, ArgumentType.Literal("clear"));
        command.addSyntax((sender, context) -> {
            Portal found = found(sender, context.get(portal));
            if (found != null) {
                info(sender, found);
            }
        }, portal, ArgumentType.Literal("list"));
        return command;
    }

    private void editActions(CommandSender sender, String name,
                             java.util.function.Function<Portal, @Nullable List<Object>> change) {
        Portal found = found(sender, name);
        if (found == null) {
            return;
        }
        List<Object> entries = change.apply(found);
        if (entries == null) {
            return;
        }
        portals.update(found.withActions(entries, warning -> LOGGER.warn("{}", warning)));
        set(sender, found.name(), "actions", entries.isEmpty() ? "none" : ActionEntries.describeAll(entries));
    }

    private void set(CommandSender sender, String name, String property, Object value) {
        sender.sendMessage(text.message(MessageKey.PORTAL_SET, sender, Messages.text("name", name),
                Messages.text("property", property), Messages.text("value", value)));
    }

    private void invalidAction(CommandSender sender, String error) {
        sender.sendMessage(text.message(MessageKey.PORTAL_ACTION_INVALID, sender, Messages.text("error", error),
                Messages.text("types", String.join(", ", ActionParser.typeNames()))));
    }

    /** {@code 500ms}, {@code 2s}, {@code 2} (seconds) or {@code off}, in milliseconds; {@code null} if unreadable. */
    static @Nullable Long millis(String value) {
        String written = value.strip().toLowerCase(Locale.ROOT);
        if (written.equals("off") || written.equals("disabled")) {
            return 0L;
        }
        try {
            if (written.endsWith("ms")) {
                return Math.max(0, Long.parseLong(written.substring(0, written.length() - 2)));
            }
            if (written.endsWith("s")) {
                written = written.substring(0, written.length() - 1);
            }
            return Math.max(0, Math.round(Double.parseDouble(written) * MILLIS_PER_SECOND));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
