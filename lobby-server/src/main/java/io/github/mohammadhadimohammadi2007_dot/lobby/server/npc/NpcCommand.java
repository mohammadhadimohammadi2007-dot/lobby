package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionEntries;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /npc}, with the subcommand names of FancyNpcs (create, remove, copy, list, nearby, info,
 * teleport, move_here, move_to, type, skin, displayname, equipment, glowing, turn_to_player,
 * turn_to_player_distance, visibility_distance, interaction_cooldown, action), plus {@code name} for the
 * multi-line name tag, {@code permission} and {@code import}. See docs/npcs.md.
 */
public final class NpcCommand extends Command {

    private static final double DEFAULT_NEARBY_RADIUS = 16;
    private static final int MAX_NEARBY_RADIUS = 128;
    private static final int LIST_LIMIT = 50;
    private static final long MILLIS_PER_SECOND = 1000;

    private final NpcCommandSupport support;
    private final Path dataDir;

    public NpcCommand(NpcService npcs, LobbyText text, PermissionService permissions, Path dataDir) {
        super("npc", "npcs");
        this.support = new NpcCommandSupport(npcs, text);
        this.dataDir = dataDir;
        setCondition((sender, commandString) -> {
            boolean has = permissions.hasPermission(sender, Permissions.COMMAND_NPC);
            if (!has && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return has;
        });
        setDefaultExecutor((sender, context) -> support.usage(sender));

        ArgumentWord npc = support.npcArgument();
        addSubcommand(create());
        addSubcommand(support.withNpc("remove", npc, this::remove, "delete"));
        addSubcommand(copy(npc));
        addSubcommand(list());
        addSubcommand(nearby());
        addSubcommand(support.withNpc("info", npc, this::info));
        addSubcommand(support.withNpc("teleport", npc, NpcCommand::teleport, "tp", "goto"));
        addSubcommand(support.withNpc("move_here", npc, this::moveHere, "movehere"));
        addSubcommand(moveTo(npc));
        addSubcommand(type(npc));
        addSubcommand(skin(npc));
        addSubcommand(toggle("glowing", npc, NpcData::glowing, NpcData::glowing));
        addSubcommand(toggle("turn_to_player", npc, NpcData::turnToPlayer, NpcData::turnToPlayer));
        addSubcommand(distance("turn_to_player_distance", npc, NpcData::turnDistance));
        addSubcommand(distance("visibility_distance", npc, NpcData::viewDistance));
        addSubcommand(interactionCooldown(npc));
        addSubcommand(permission(npc));
        addSubcommand(equipment(npc));
        new NpcNameCommands(support).addTo(this, npc);
        new NpcActionCommands(support).addTo(this, npc);
        addSubcommand(importCommand());
    }

    private Command create() {
        Command command = new Command("create");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        ArgumentWord name = ArgumentType.Word("name");
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(support.text.message(MessageKey.PLAYERS_ONLY, sender));
                return;
            }
            String wanted = context.get(name).toLowerCase(Locale.ROOT);
            if (!NpcService.validName(wanted)) {
                sender.sendMessage(support.text.message(MessageKey.NPC_INVALID_NAME, sender,
                        Messages.text("name", context.get(name))));
                return;
            }
            if (support.npcs.create(wanted, player.getPosition()) == null) {
                sender.sendMessage(support.text.message(MessageKey.NPC_EXISTS, sender, Messages.text("name", wanted)));
                return;
            }
            sender.sendMessage(support.text.message(MessageKey.NPC_CREATED, sender, Messages.text("name", wanted)));
        }, name);
        return command;
    }

    private void remove(CommandSender sender, NpcData npc) {
        support.npcs.delete(npc.name());
        sender.sendMessage(support.text.message(MessageKey.NPC_DELETED, sender, Messages.text("name", npc.name())));
    }

    private Command copy(ArgumentWord npc) {
        Command command = new Command("copy", "clone");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        ArgumentWord newName = ArgumentType.Word("name");
        command.addSyntax((sender, context) -> {
            NpcData source = support.found(sender, context.get(npc));
            if (source == null) {
                return;
            }
            String target = context.get(newName).toLowerCase(Locale.ROOT);
            if (!NpcService.validName(target)) {
                sender.sendMessage(support.text.message(MessageKey.NPC_INVALID_NAME, sender,
                        Messages.text("name", target)));
                return;
            }
            if (support.npcs.copy(source, target) == null) {
                sender.sendMessage(support.text.message(MessageKey.NPC_EXISTS, sender, Messages.text("name", target)));
                return;
            }
            sender.sendMessage(support.text.message(MessageKey.NPC_COPIED, sender,
                    Messages.text("name", source.name()), Messages.text("new-name", target)));
        }, npc, newName);
        return command;
    }

    private Command list() {
        Command command = new Command("list");
        command.setDefaultExecutor((sender, context) -> listed(sender, support.npcs.all(), null));
        return command;
    }

    private Command nearby() {
        Command command = new Command("nearby", "near");
        ArgumentNumber<Integer> radius = ArgumentType.Integer("radius").min(1).max(MAX_NEARBY_RADIUS);
        command.setDefaultExecutor((sender, context) -> nearby(sender, DEFAULT_NEARBY_RADIUS));
        command.addSyntax((sender, context) -> nearby(sender, context.get(radius)), radius);
        return command;
    }

    private void nearby(CommandSender sender, double radius) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(support.text.message(MessageKey.PLAYERS_ONLY, sender));
            return;
        }
        listed(sender, support.npcs.near(player.getPosition(), radius), player.getPosition());
    }

    private void listed(CommandSender sender, List<NpcData> npcs, Pos from) {
        if (npcs.isEmpty()) {
            sender.sendMessage(support.text.message(MessageKey.NPC_LIST_EMPTY, sender));
            return;
        }
        String names = npcs.stream().limit(LIST_LIMIT)
                .map(data -> from == null ? data.name()
                        : data.name() + " (" + (int) data.position().distance(from) + "m)")
                .collect(Collectors.joining(", "));
        sender.sendMessage(support.text.message(MessageKey.NPC_LIST, sender,
                Messages.text("count", npcs.size()), Messages.text("names", names)));
    }

    private void info(CommandSender sender, NpcData npc) {
        Pos position = npc.position();
        String actions = npc.allActions().isEmpty() ? "none" : npc.allActions().entrySet().stream()
                .map(entry -> entry.getKey().commandName() + ": " + ActionEntries.describeAll(entry.getValue().entries()))
                .collect(Collectors.joining("; "));
        sender.sendMessage(support.text.message(MessageKey.NPC_INFO, sender,
                Messages.text("name", npc.name()),
                Messages.text("type", npc.type().key().value()),
                Messages.text("x", round(position.x())), Messages.text("y", round(position.y())),
                Messages.text("z", round(position.z())),
                Messages.text("skin", npc.isPlayer() ? npc.skin().describe() : "-"),
                Messages.text("turn", npc.turnToPlayer() ? "yes, within " + (int) npc.turnDistance() + " blocks" : "no"),
                Messages.text("glowing", npc.glowing() ? "yes" : "no"),
                Messages.text("lines", npc.nameTag().lines().size()),
                Messages.text("view-distance", (int) npc.viewDistance()),
                Messages.text("permission", npc.permission().isEmpty() ? "everyone" : npc.permission()),
                Messages.text("actions", actions)));
    }

    private static void teleport(CommandSender sender, NpcData npc) {
        if (sender instanceof Player player) {
            player.teleport(npc.position());
        }
    }

    private void moveHere(CommandSender sender, NpcData npc) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(support.text.message(MessageKey.PLAYERS_ONLY, sender));
            return;
        }
        npc.position(player.getPosition());
        support.npcs.changed(npc);
        sender.sendMessage(support.text.message(MessageKey.NPC_MOVED, sender, Messages.text("name", npc.name())));
    }

    private Command moveTo(ArgumentWord npc) {
        Command command = new Command("move_to", "moveto");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        var x = ArgumentType.Double("x");
        var y = ArgumentType.Double("y");
        var z = ArgumentType.Double("z");
        var yaw = ArgumentType.Float("yaw");
        var pitch = ArgumentType.Float("pitch");
        command.addSyntax((sender, context) -> moveTo(sender, context.get(npc),
                context.get(x), context.get(y), context.get(z), null, null), npc, x, y, z);
        command.addSyntax((sender, context) -> moveTo(sender, context.get(npc), context.get(x), context.get(y),
                context.get(z), context.get(yaw), context.get(pitch)), npc, x, y, z, yaw, pitch);
        return command;
    }

    private void moveTo(CommandSender sender, String name, double x, double y, double z, Float yaw, Float pitch) {
        NpcData npc = support.found(sender, name);
        if (npc == null) {
            return;
        }
        Pos current = npc.position();
        npc.position(new Pos(x, y, z, yaw == null ? current.yaw() : yaw, pitch == null ? current.pitch() : pitch));
        support.npcs.changed(npc);
        sender.sendMessage(support.text.message(MessageKey.NPC_MOVED, sender, Messages.text("name", npc.name())));
    }

    private Command type(ArgumentWord npc) {
        Command command = new Command("type");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        ArgumentWord type = ArgumentType.Word("type");
        type.setSuggestionCallback((sender, context, suggestion) -> {
            for (EntityType entityType : EntityType.values()) {
                suggestion.addEntry(new SuggestionEntry(entityType.key().value()));
            }
        });
        command.addSyntax((sender, context) -> {
            NpcData data = support.found(sender, context.get(npc));
            if (data == null) {
                return;
            }
            try {
                data.type(NpcCodec.entityType(context.get(type)));
            } catch (DataException e) {
                support.invalid(sender, "type", e.getMessage(), "an entity type such as player, villager or zombie");
                return;
            }
            support.changed(sender, data, "type", data.type().key().value());
        }, npc, type);
        return command;
    }

    private Command skin(ArgumentWord npc) {
        Command command = new Command("skin");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        // A greedy argument keeps a link whole whatever it contains.
        var skinText = ArgumentType.StringArray("skin");
        skinText.setSuggestionCallback((sender, context, suggestion) -> {
            for (String word : List.of("@none", "@mirror")) {
                suggestion.addEntry(new SuggestionEntry(word));
            }
        });
        command.addSyntax((sender, context) -> skin(sender, context.get(npc), String.join(" ", context.get(skinText))),
                npc, skinText);
        return command;
    }

    private void skin(CommandSender sender, String name, String written) {
        NpcData npc = support.found(sender, name);
        if (npc == null) {
            return;
        }
        if (!npc.isPlayer()) {
            sender.sendMessage(support.text.message(MessageKey.NPC_NOT_A_PLAYER, sender,
                    Messages.text("name", npc.name()), Messages.text("type", npc.type().key().value())));
            return;
        }
        NpcSkin skin;
        try {
            skin = NpcSkin.parse(written);
        } catch (NpcSkin.SkinException e) {
            support.invalid(sender, "skin", e.getMessage(), "a player name, @mirror, @none, sr:<name> or a MineSkin link");
            return;
        }
        if (skin.fetched()) {
            sender.sendMessage(support.text.message(MessageKey.NPC_SKIN_LOOKING_UP, sender,
                    Messages.text("name", npc.name()), Messages.text("skin", skin.describe())));
        }
        support.npcs.setSkin(npc, skin).whenComplete((result, error) -> Async.onTickThread(() -> {
            if (error != null || (skin.fetched() && !result.found())) {
                sender.sendMessage(support.text.message(MessageKey.NPC_SKIN_FAILED, sender,
                        Messages.text("name", npc.name()),
                        Messages.text("error", error != null ? String.valueOf(error.getMessage()) : result.problem())));
                return;
            }
            sender.sendMessage(support.text.message(MessageKey.NPC_SKIN_SET, sender,
                    Messages.text("name", npc.name()), Messages.text("skin", skin.describe())));
        }));
    }

    /** A FancyNpcs-style switch: {@code <npc> [true|false|toggle]}, toggling when nothing is given. */
    private Command toggle(String name, ArgumentWord npc, java.util.function.Predicate<NpcData> current,
                           java.util.function.BiConsumer<NpcData, Boolean> set) {
        Command command = new Command(name, name.replace("_", ""));
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        ArgumentWord state = ArgumentType.Word("state").from("true", "false", "toggle");
        command.addSyntax((sender, context) -> toggle(sender, context.get(npc), null, name, current, set), npc);
        command.addSyntax((sender, context) -> toggle(sender, context.get(npc), context.get(state), name, current, set),
                npc, state);
        return command;
    }

    private void toggle(CommandSender sender, String npcName, String value, String property,
                        java.util.function.Predicate<NpcData> current, java.util.function.BiConsumer<NpcData, Boolean> set) {
        NpcData npc = support.found(sender, npcName);
        if (npc == null) {
            return;
        }
        Boolean wanted = NpcCommandSupport.state(value, current.test(npc));
        if (wanted == null) {
            support.invalid(sender, property, "not true, false or toggle", "true, false or toggle");
            return;
        }
        set.accept(npc, wanted);
        support.changed(sender, npc, property, wanted);
    }

    /** {@code <npc> <blocks>} for the two distances. */
    private Command distance(String name, ArgumentWord npc, java.util.function.ObjDoubleConsumer<NpcData> set) {
        Command command = new Command(name, name.replace("_", ""));
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        var blocks = ArgumentType.Double("distance").min(1.0).max(128.0);
        command.addSyntax((sender, context) -> {
            NpcData data = support.found(sender, context.get(npc));
            if (data != null) {
                set.accept(data, context.get(blocks));
                support.changed(sender, data, name, context.get(blocks));
            }
        }, npc, blocks);
        return command;
    }

    /** FancyNpcs takes a duration such as {@code 5s} or {@code 500ms}, or {@code disabled}. */
    private Command interactionCooldown(ArgumentWord npc) {
        Command command = new Command("interaction_cooldown", "interactioncooldown");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        ArgumentWord cooldown = ArgumentType.Word("cooldown");
        command.addSyntax((sender, context) -> {
            NpcData data = support.found(sender, context.get(npc));
            if (data == null) {
                return;
            }
            Long millis = millis(context.get(cooldown));
            if (millis == null) {
                support.invalid(sender, "interaction_cooldown", "not a duration", "5s, 500ms or disabled");
                return;
            }
            support.npcs.setClickCooldown(data, millis);
            support.changed(sender, data, "interaction_cooldown", millis + " ms");
        }, npc, cooldown);
        return command;
    }

    /** {@code 500ms}, {@code 2s}, {@code 2} (seconds) or {@code disabled}, in milliseconds. */
    static Long millis(String value) {
        String text = value.strip().toLowerCase(Locale.ROOT);
        if (text.equals("disabled") || text.equals("off")) {
            return 0L;
        }
        try {
            if (text.endsWith("ms")) {
                return Math.max(0, Long.parseLong(text.substring(0, text.length() - 2)));
            }
            if (text.endsWith("s")) {
                text = text.substring(0, text.length() - 1);
            }
            return Math.max(0, Math.round(Double.parseDouble(text) * MILLIS_PER_SECOND));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Command permission(ArgumentWord npc) {
        Command command = new Command("permission");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        ArgumentWord node = ArgumentType.Word("permission");
        command.addSyntax((sender, context) -> {
            NpcData data = support.found(sender, context.get(npc));
            if (data != null) {
                String value = context.get(node);
                data.permission(value.equals("\"\"") || value.equalsIgnoreCase("@none") ? "" : value);
                support.changed(sender, data, "permission", data.permission().isEmpty() ? "everyone" : data.permission());
            }
        }, npc, node);
        return command;
    }

    private Command equipment(ArgumentWord npc) {
        Command command = new Command("equipment");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        ArgumentWord set = ArgumentType.Word("set").from("set");
        ArgumentWord list = ArgumentType.Word("list").from("list");
        ArgumentWord clear = ArgumentType.Word("clear").from("clear");
        ArgumentWord slot = ArgumentType.Word("slot");
        slot.setSuggestionCallback((sender, context, suggestion) -> {
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                suggestion.addEntry(new SuggestionEntry(NpcCodec.slotName(equipmentSlot)));
            }
        });
        ArgumentWord item = ArgumentType.Word("item");
        command.addSyntax((sender, context) -> {
            NpcData data = support.found(sender, context.get(npc));
            if (data == null) {
                return;
            }
            EquipmentSlot equipmentSlot = NpcCodec.slot(context.get(slot));
            String itemName = context.get(item).toLowerCase(Locale.ROOT);
            Material material = itemName.equals("air") || itemName.equals("none") ? Material.AIR
                    : Material.fromKey(itemName.contains(":") ? itemName : "minecraft:" + itemName);
            if (equipmentSlot == null || material == null) {
                support.invalid(sender, "equipment", "unknown slot or item", "main_hand, off_hand, helmet,"
                        + " chestplate, leggings or boots, and an item name");
                return;
            }
            data.equipment(equipmentSlot, material);
            support.changed(sender, data, "equipment " + NpcCodec.slotName(equipmentSlot), material.key().value());
        }, npc, set, slot, item);
        command.addSyntax((sender, context) -> {
            NpcData data = support.found(sender, context.get(npc));
            if (data != null) {
                String items = data.equipment().isEmpty() ? "nothing" : data.equipment().entrySet().stream()
                        .map(entry -> NpcCodec.slotName(entry.getKey()) + ": " + entry.getValue().key().value())
                        .collect(Collectors.joining(", "));
                sender.sendMessage(support.text.message(MessageKey.NPC_EQUIPMENT_LIST, sender,
                        Messages.text("name", data.name()), Messages.text("items", items)));
            }
        }, npc, list);
        command.addSyntax((sender, context) -> {
            NpcData data = support.found(sender, context.get(npc));
            if (data != null) {
                for (Map.Entry<EquipmentSlot, Material> entry : data.equipment().entrySet()) {
                    data.equipment(entry.getKey(), null);
                }
                support.changed(sender, data, "equipment", "nothing");
            }
        }, npc, clear);
        return command;
    }

    /** Imports FancyNpcs' npcs.yml, in the background because it reads from disk. */
    private Command importCommand() {
        Command command = new Command("import");
        command.setDefaultExecutor((sender, context) -> Async.supply(() -> FancyNpcsImport.run(dataDir, support.npcs))
                .whenComplete((result, error) -> Async.onTickThread(() -> {
                    if (error != null) {
                        sender.sendMessage(support.text.message(MessageKey.NPC_IMPORT_SKIPPED, sender,
                                Messages.text("entry", String.valueOf(error.getMessage()))));
                        return;
                    }
                    for (String skipped : result.skipped()) {
                        sender.sendMessage(support.text.message(MessageKey.NPC_IMPORT_SKIPPED, sender,
                                Messages.text("entry", skipped)));
                    }
                    if (result.imported().isEmpty()) {
                        sender.sendMessage(support.text.message(MessageKey.NPC_IMPORT_NOTHING, sender,
                                Messages.text("file", result.file().toString())));
                        return;
                    }
                    sender.sendMessage(support.text.message(MessageKey.NPC_IMPORTED, sender,
                            Messages.text("count", result.imported().size()),
                            Messages.text("file", result.file().getFileName().toString())));
                })));
        return command;
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
