package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import net.minestom.server.command.builder.condition.CommandCondition;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Pos;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /hologram} with the same subcommands as FancyHolograms, plus what this lobby adds
 * (animations live in the file, see docs/holograms.md):
 *
 * <pre>
 * /hologram list | near [radius] | info &lt;name&gt;
 * /hologram create &lt;name&gt; [text|item|block] [item or block name]
 * /hologram delete &lt;name&gt; | copy &lt;name&gt; &lt;new name&gt;
 * /hologram teleport &lt;name&gt;        (brings the hologram to you)
 * /hologram goto &lt;name&gt;            (brings you to the hologram)
 * /hologram addline &lt;name&gt; &lt;text&gt;
 * /hologram setline | insertline &lt;name&gt; &lt;number&gt; &lt;text&gt;
 * /hologram removeline &lt;name&gt; &lt;number&gt;
 * /hologram set &lt;name&gt; &lt;property&gt; &lt;value&gt;
 * /hologram action add &lt;name&gt; &lt;action&gt; | action clear &lt;name&gt; | action list &lt;name&gt;
 * /hologram import
 * </pre>
 */
public final class HologramCommand extends Command {

    private static final double DEFAULT_NEAR_RADIUS = 16;
    private static final int MAX_NEAR_RADIUS = 128;
    private static final int LIST_LIMIT = 50;

    private final HologramService holograms;
    private final LobbyText text;
    private final Path dataDir;

    public HologramCommand(HologramService holograms, LobbyText text, PermissionService permissions, Path dataDir) {
        super("hologram", "holograms", "hd");
        this.holograms = holograms;
        this.text = text;
        this.dataDir = dataDir;
        CommandCondition allowed = (sender, commandString) -> {
            boolean has = permissions.hasPermission(sender, Permissions.COMMAND_HOLOGRAM);
            if (!has && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return has;
        };
        setCondition(allowed);
        setDefaultExecutor((sender, context) -> usage(sender));

        ArgumentWord name = name();
        ArgumentWord type = ArgumentType.Word("type").from(HologramType.configNames().toArray(String[]::new));
        ArgumentWord material = ArgumentType.Word("material");
        ArgumentWord newName = ArgumentType.Word("new-name");
        ArgumentWord property = ArgumentType.Word("property").from(HologramProperties.names().toArray(String[]::new));
        var value = ArgumentType.StringArray("value");
        var lineText = ArgumentType.StringArray("text");
        var actionText = ArgumentType.StringArray("action");
        ArgumentNumber<Integer> lineNumber = ArgumentType.Integer("line").min(1).max(HologramData.MAX_LINES);
        ArgumentNumber<Integer> radius = ArgumentType.Integer("radius").min(1).max(MAX_NEAR_RADIUS);

        addSubcommand(simple("list", (sender, context) -> list(sender)));
        addSubcommand(near(radius));
        addSubcommand(withName("info", name, (sender, data) -> info(sender, data)));
        addSubcommand(create(name, type, material));
        addSubcommand(withName("delete", name, (sender, data) -> delete(sender, data), "remove"));
        addSubcommand(copy(name, newName));
        // The names FancyHolograms uses: "teleport" brings you to the hologram, "movehere" the hologram to you.
        addSubcommand(withName("movehere", name, this::moveHere, "here", "position"));
        addSubcommand(withName("teleport", name, HologramCommand::goTo, "goto", "tp"));
        addSubcommand(addLine(name, lineText));
        addSubcommand(lineAt("setline", name, lineNumber, lineText, Insert.REPLACE));
        addSubcommand(lineAt("insertline", name, lineNumber, lineText, Insert.BEFORE, "insertbefore"));
        addSubcommand(lineAt("insertafter", name, lineNumber, lineText, Insert.AFTER));
        addSubcommand(removeLine(name, lineNumber));
        addSubcommand(set(name, property, value));
        addSubcommand(actions(name, actionText));
        addSubcommand(simple("import", (sender, context) -> importFile(sender)));
    }

    /** A name argument that suggests the holograms that exist. */
    private ArgumentWord name() {
        ArgumentWord argument = ArgumentType.Word("name");
        argument.setSuggestionCallback((sender, context, suggestion) -> {
            for (HologramData data : holograms.all()) {
                suggestion.addEntry(new SuggestionEntry(data.name()));
            }
        });
        return argument;
    }

    private Command simple(String name, net.minestom.server.command.builder.CommandExecutor executor,
                           String... aliases) {
        Command command = new Command(name, aliases);
        command.setDefaultExecutor(executor);
        return command;
    }

    /** A subcommand that only needs an existing hologram. */
    private Command withName(String name, ArgumentWord nameArgument, OnHologram action, String... aliases) {
        Command command = new Command(name, aliases);
        command.setDefaultExecutor((sender, context) -> usage(sender));
        command.addSyntax((sender, context) -> {
            HologramData data = found(sender, context.get(nameArgument));
            if (data != null) {
                action.run(sender, data);
            }
        }, nameArgument);
        return command;
    }

    private interface OnHologram {
        void run(CommandSender sender, HologramData data);
    }

    private Command create(ArgumentWord name, ArgumentWord type, ArgumentWord material) {
        Command command = new Command("create", "add");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        command.addSyntax((sender, context) -> create(sender, context.get(name), HologramType.TEXT, null), name);
        command.addSyntax((sender, context) ->
                create(sender, context.get(name), HologramType.fromConfigName(context.get(type)), null), name, type);
        command.addSyntax((sender, context) -> create(sender, context.get(name),
                HologramType.fromConfigName(context.get(type)), context.get(material)), name, type, material);
        return command;
    }

    private Command copy(ArgumentWord name, ArgumentWord newName) {
        Command command = new Command("copy", "clone");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        command.addSyntax((sender, context) -> {
            HologramData source = found(sender, context.get(name));
            if (source == null) {
                return;
            }
            String target = context.get(newName).toLowerCase(Locale.ROOT);
            if (!HologramService.validName(target)) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_INVALID_NAME, sender,
                        Messages.text("name", target)));
                return;
            }
            if (holograms.copy(source, target) == null) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_EXISTS, sender, Messages.text("name", target)));
                return;
            }
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_COPIED, sender,
                    Messages.text("name", source.name()), Messages.text("new-name", target)));
        }, name, newName);
        return command;
    }

    private Command near(ArgumentNumber<Integer> radius) {
        Command command = new Command("near", "nearby");
        command.setDefaultExecutor((sender, context) -> near(sender, DEFAULT_NEAR_RADIUS));
        command.addSyntax((sender, context) -> near(sender, context.get(radius)), radius);
        return command;
    }

    private Command addLine(ArgumentWord name, net.minestom.server.command.builder.arguments.ArgumentStringArray lineText) {
        Command command = new Command("addline");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        command.addSyntax((sender, context) -> {
            HologramData data = foundText(sender, context.get(name));
            if (data == null) {
                return;
            }
            List<String> lines = new ArrayList<>(data.lines());
            if (lines.size() >= HologramData.MAX_LINES) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_TOO_MANY_LINES, sender,
                        Messages.text("max", HologramData.MAX_LINES)));
                return;
            }
            lines.add(line(context, lineText));
            data.lines(lines);
            holograms.changed(data);
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_LINE_ADDED, sender,
                    Messages.text("name", data.name()), Messages.text("line", lines.size())));
        }, name, lineText);
        return command;
    }

    /** What {@code setline}, {@code insertline} and {@code insertafter} do with the line number. */
    private enum Insert {
        REPLACE,
        BEFORE,
        AFTER
    }

    private Command lineAt(String commandName, ArgumentWord name, ArgumentNumber<Integer> lineNumber,
                           net.minestom.server.command.builder.arguments.ArgumentStringArray lineText,
                           Insert insert, String... aliases) {
        Command command = new Command(commandName, aliases);
        command.setDefaultExecutor((sender, context) -> usage(sender));
        command.addSyntax((sender, context) -> {
            HologramData data = foundText(sender, context.get(name));
            if (data == null) {
                return;
            }
            int number = context.get(lineNumber);
            List<String> lines = new ArrayList<>(data.lines());
            int limit = insert == Insert.REPLACE ? lines.size() : lines.size() + 1;
            if (number > limit) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_NO_SUCH_LINE, sender,
                        Messages.text("line", number), Messages.text("lines", lines.size())));
                return;
            }
            switch (insert) {
                case REPLACE -> lines.set(number - 1, line(context, lineText));
                case BEFORE -> lines.add(number - 1, line(context, lineText));
                case AFTER -> lines.add(Math.min(number, lines.size()), line(context, lineText));
            }
            data.lines(lines);
            holograms.changed(data);
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_LINE_SET, sender,
                    Messages.text("name", data.name()), Messages.text("line", number)));
        }, name, lineNumber, lineText);
        return command;
    }

    private Command removeLine(ArgumentWord name, ArgumentNumber<Integer> lineNumber) {
        Command command = new Command("removeline");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        command.addSyntax((sender, context) -> {
            HologramData data = foundText(sender, context.get(name));
            if (data == null) {
                return;
            }
            int number = context.get(lineNumber);
            List<String> lines = new ArrayList<>(data.lines());
            if (number > lines.size()) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_NO_SUCH_LINE, sender,
                        Messages.text("line", number), Messages.text("lines", lines.size())));
                return;
            }
            lines.remove(number - 1);
            data.lines(lines);
            holograms.changed(data);
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_LINE_REMOVED, sender,
                    Messages.text("name", data.name()), Messages.text("line", number)));
        }, name, lineNumber);
        return command;
    }

    private Command set(ArgumentWord name, ArgumentWord property,
                        net.minestom.server.command.builder.arguments.ArgumentStringArray value) {
        Command command = new Command("set", "edit");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(
                text.message(MessageKey.HOLOGRAM_PROPERTIES, sender,
                        Messages.text("properties", String.join(", ", HologramProperties.names())))));
        command.addSyntax((sender, context) -> {
            HologramData data = found(sender, context.get(name));
            if (data == null) {
                return;
            }
            String propertyName = context.get(property);
            String lineCommand = HologramProperties.lineCommandFor(propertyName);
            if (lineCommand != null) {
                // FancyHolograms edits lines with "edit <name> addline ..."; here they are their own commands.
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_USE_LINE_COMMAND, sender,
                        Messages.text("command", "/hologram " + lineCommand + " " + data.name())));
                return;
            }
            HologramProperties.Result result = HologramProperties.apply(data, propertyName,
                    String.join(" ", context.get(value)));
            if (result == null) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_PROPERTIES, sender,
                        Messages.text("properties", String.join(", ", HologramProperties.names()))));
                return;
            }
            if (result.failed()) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_PROPERTY_INVALID, sender,
                        Messages.text("property", propertyName), Messages.text("error", result.error()),
                        Messages.text("allowed", HologramProperties.allowed(propertyName))));
                return;
            }
            holograms.changed(data);
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_PROPERTY_SET, sender,
                    Messages.text("name", data.name()), Messages.text("property", propertyName),
                    Messages.text("value", result.shown())));
        }, name, property, value);
        return command;
    }

    private Command actions(ArgumentWord name,
                            net.minestom.server.command.builder.arguments.ArgumentStringArray actionText) {
        Command command = new Command("action", "actions");
        command.setDefaultExecutor((sender, context) -> usage(sender));
        ArgumentWord add = ArgumentType.Word("add").from("add");
        ArgumentWord clear = ArgumentType.Word("clear").from("clear");
        ArgumentWord list = ArgumentType.Word("list").from("list");
        command.addSyntax((sender, context) -> {
            HologramData data = found(sender, context.get(name));
            if (data == null) {
                return;
            }
            String line = String.join(" ", context.get(actionText));
            try {
                ActionParser.parse(line);
            } catch (ActionParser.ActionException e) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_ACTION_INVALID, sender,
                        Messages.text("error", e.getMessage()),
                        Messages.text("types", String.join(", ", ActionParser.typeNames()))));
                return;
            }
            List<String> lines = new ArrayList<>(data.actionLines());
            lines.add(line);
            holograms.setActions(data, lines, data.actions().cooldownMillis());
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_ACTION_ADDED, sender,
                    Messages.text("name", data.name()), Messages.text("action", line)));
        }, add, name, actionText);
        command.addSyntax((sender, context) -> {
            HologramData data = found(sender, context.get(name));
            if (data != null) {
                holograms.setActions(data, List.of(), data.actions().cooldownMillis());
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_ACTIONS_CLEARED, sender,
                        Messages.text("name", data.name())));
            }
        }, clear, name);
        command.addSyntax((sender, context) -> {
            HologramData data = found(sender, context.get(name));
            if (data != null) {
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_ACTION_LIST, sender,
                        Messages.text("name", data.name()),
                        Messages.text("actions", data.actionLines().isEmpty() ? "none"
                                : String.join(" | ", data.actionLines()))));
            }
        }, list, name);
        return command;
    }

    private void create(CommandSender sender, String rawName, @Nullable HologramType type, @Nullable String material) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
            return;
        }
        String name = rawName.toLowerCase(Locale.ROOT);
        if (!HologramService.validName(name)) {
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_INVALID_NAME, sender, Messages.text("name", rawName)));
            return;
        }
        HologramType wanted = type == null ? HologramType.TEXT : type;
        HologramData data = holograms.create(name, wanted, player.getPosition(),
                wanted == HologramType.TEXT ? List.of("<yellow>" + name) : List.of());
        if (data == null) {
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_EXISTS, sender, Messages.text("name", name)));
            return;
        }
        if (material != null) {
            HologramProperties.Result result = HologramProperties.apply(data,
                    wanted == HologramType.BLOCK ? "block" : "item", material);
            if (result != null && result.failed()) {
                holograms.delete(name);
                sender.sendMessage(text.message(MessageKey.HOLOGRAM_PROPERTY_INVALID, sender,
                        Messages.text("property", wanted.configName()), Messages.text("error", result.error()),
                        Messages.text("allowed", material)));
                return;
            }
        }
        if (wanted != HologramType.TEXT && data.item() == null && data.block() == null) {
            holograms.delete(name);
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_NEEDS_MATERIAL, sender,
                    Messages.text("type", wanted.configName())));
            return;
        }
        holograms.changed(data);
        sender.sendMessage(text.message(MessageKey.HOLOGRAM_CREATED, sender, Messages.text("name", name),
                Messages.text("type", wanted.configName())));
    }

    /** Imports a FancyHolograms file in the background, because it reads from disk. */
    private void importFile(CommandSender sender) {
        Async.supply(() -> FancyHologramsImport.run(dataDir, holograms))
                .whenComplete((result, error) -> Async.onTickThread(() -> {
                    if (error != null) {
                        sender.sendMessage(text.message(MessageKey.HOLOGRAM_ACTION_INVALID, sender,
                                Messages.text("error", String.valueOf(error.getMessage())),
                                Messages.text("types", "")));
                        return;
                    }
                    for (String skipped : result.skipped()) {
                        sender.sendMessage(text.message(MessageKey.HOLOGRAM_IMPORT_SKIPPED, sender,
                                Messages.text("entry", skipped)));
                    }
                    if (result.imported().isEmpty()) {
                        sender.sendMessage(text.message(MessageKey.HOLOGRAM_IMPORT_NOTHING, sender,
                                Messages.text("file", result.file().toString())));
                        return;
                    }
                    sender.sendMessage(text.message(MessageKey.HOLOGRAM_IMPORTED, sender,
                            Messages.text("count", result.imported().size()),
                            Messages.text("file", result.file().getFileName().toString())));
                }));
    }

    private void delete(CommandSender sender, HologramData data) {
        holograms.delete(data.name());
        sender.sendMessage(text.message(MessageKey.HOLOGRAM_DELETED, sender, Messages.text("name", data.name())));
    }

    private void moveHere(CommandSender sender, HologramData data) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
            return;
        }
        data.position(player.getPosition());
        holograms.changed(data);
        sender.sendMessage(text.message(MessageKey.HOLOGRAM_MOVED, sender, Messages.text("name", data.name())));
    }

    private static void goTo(CommandSender sender, HologramData data) {
        if (sender instanceof Player player) {
            player.teleport(data.position());
        }
    }

    private void list(CommandSender sender) {
        List<HologramData> all = holograms.all();
        if (all.isEmpty()) {
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_LIST_EMPTY, sender));
            return;
        }
        sender.sendMessage(text.message(MessageKey.HOLOGRAM_LIST, sender,
                Messages.text("count", all.size()),
                Messages.text("names", all.stream().limit(LIST_LIMIT).map(HologramData::name)
                        .reduce((a, b) -> a + ", " + b).orElse(""))));
    }

    private void near(CommandSender sender, double radius) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
            return;
        }
        List<HologramData> found = holograms.near(player.getPosition(), radius);
        if (found.isEmpty()) {
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_LIST_EMPTY, sender));
            return;
        }
        sender.sendMessage(text.message(MessageKey.HOLOGRAM_LIST, sender,
                Messages.text("count", found.size()),
                Messages.text("names", found.stream().limit(LIST_LIMIT)
                        .map(data -> data.name() + " (" + (int) data.position().distance(player.getPosition()) + "m)")
                        .reduce((a, b) -> a + ", " + b).orElse(""))));
    }

    private void info(CommandSender sender, HologramData data) {
        Pos position = data.position();
        sender.sendMessage(text.message(MessageKey.HOLOGRAM_INFO, sender,
                Messages.text("name", data.name()),
                Messages.text("type", data.type().configName()),
                Messages.text("x", round(position.x())), Messages.text("y", round(position.y())),
                Messages.text("z", round(position.z())),
                Messages.text("lines", data.lines().size()),
                Messages.text("frames", data.frames().size()),
                Messages.text("scale", data.scale()),
                Messages.text("billboard", data.billboard().configName()),
                Messages.text("alignment", data.alignment().configName()),
                Messages.text("view-distance", (int) data.viewDistance()),
                Messages.text("update-interval", data.effectiveUpdateIntervalTicks()),
                Messages.text("permission", data.permission().isEmpty() ? "everyone" : data.permission()),
                Messages.text("actions", data.actionLines().isEmpty() ? "none"
                        : String.join(" | ", data.actionLines()))));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(text.message(MessageKey.HOLOGRAM_USAGE, sender));
    }

    /** The hologram with that name, or {@code null} after telling the sender it does not exist. */
    private @Nullable HologramData found(CommandSender sender, String name) {
        HologramData data = holograms.get(name);
        if (data == null) {
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_UNKNOWN, sender, Messages.text("name", name)));
        }
        return data;
    }

    /** Like {@link #found} but also refuses item and block holograms, which have no lines. */
    private @Nullable HologramData foundText(CommandSender sender, String name) {
        HologramData data = found(sender, name);
        if (data != null && data.type() != HologramType.TEXT) {
            sender.sendMessage(text.message(MessageKey.HOLOGRAM_WRONG_TYPE, sender,
                    Messages.text("name", data.name()), Messages.text("type", data.type().configName())));
            return null;
        }
        return data;
    }

    private static String line(CommandContext context,
                               net.minestom.server.command.builder.arguments.ArgumentStringArray argument) {
        return String.join(" ", context.get(argument));
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
