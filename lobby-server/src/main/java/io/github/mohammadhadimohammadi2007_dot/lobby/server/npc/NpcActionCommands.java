package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionEntries;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /npc action <npc> <trigger> ...}, with FancyNpcs' subcommands:
 *
 * <pre>
 * add &lt;action&gt;              add_before|add_after|set &lt;number&gt; &lt;action&gt;
 * remove &lt;number&gt;           move_up|move_down &lt;number&gt;
 * clear                      list
 * </pre>
 *
 * The trigger is {@code any_click}, {@code left_click} or {@code right_click}. An action is written as
 * this lobby writes it ({@code message: Hello}) or as FancyNpcs does ({@code message Hello}); see
 * {@link FancyNpcsActions}.
 */
final class NpcActionCommands {

    /** What a list edit does with its number. */
    private enum Place {
        END,
        BEFORE,
        AFTER,
        REPLACE
    }

    private final NpcCommandSupport support;

    NpcActionCommands(NpcCommandSupport support) {
        this.support = support;
    }

    /** Adds {@code action} to {@code /npc}. */
    void addTo(Command npcCommand, ArgumentWord npc) {
        Command command = new Command("action", "actions");
        command.setDefaultExecutor((sender, context) -> support.usage(sender));
        ArgumentWord trigger = ArgumentType.Word("trigger").from(NpcTrigger.commandNames().stream().sorted()
                .toArray(String[]::new));
        ArgumentStringArray action = ArgumentType.StringArray("action");
        ArgumentNumber<Integer> number = ArgumentType.Integer("number").min(1);

        command.addSyntax((sender, context) -> edit(sender, context.get(npc), context.get(trigger), 0,
                String.join(" ", context.get(action)), Place.END), npc, trigger, ArgumentType.Literal("add"), action);
        addPlaced(command, npc, trigger, number, action, "add_before", Place.BEFORE);
        addPlaced(command, npc, trigger, number, action, "add_after", Place.AFTER);
        addPlaced(command, npc, trigger, number, action, "set", Place.REPLACE);
        command.addSyntax((sender, context) -> remove(sender, context.get(npc), context.get(trigger),
                context.get(number)), npc, trigger, ArgumentType.Literal("remove"), number);
        command.addSyntax((sender, context) -> move(sender, context.get(npc), context.get(trigger),
                context.get(number), -1), npc, trigger, ArgumentType.Literal("move_up"), number);
        command.addSyntax((sender, context) -> move(sender, context.get(npc), context.get(trigger),
                context.get(number), 1), npc, trigger, ArgumentType.Literal("move_down"), number);
        command.addSyntax((sender, context) -> clear(sender, context.get(npc), context.get(trigger)),
                npc, trigger, ArgumentType.Literal("clear"));
        command.addSyntax((sender, context) -> list(sender, context.get(npc), context.get(trigger)),
                npc, trigger, ArgumentType.Literal("list"));
        npcCommand.addSubcommand(command);
    }

    private void addPlaced(Command command, ArgumentWord npc, ArgumentWord trigger, ArgumentNumber<Integer> number,
                           ArgumentStringArray action, String literal, Place place) {
        command.addSyntax((sender, context) -> edit(sender, context.get(npc), context.get(trigger),
                context.get(number), String.join(" ", context.get(action)), place),
                npc, trigger, ArgumentType.Literal(literal), number, action);
    }

    private void edit(CommandSender sender, String npcName, String triggerName, int number, String written, Place place) {
        NpcData npc = support.found(sender, npcName);
        NpcTrigger trigger = NpcTrigger.fromCommandName(triggerName);
        if (npc == null || trigger == null) {
            return;
        }
        String line;
        try {
            line = FancyNpcsActions.typed(written);
        } catch (ActionParser.ActionException e) {
            sender.sendMessage(support.text.message(MessageKey.NPC_ACTION_INVALID, sender,
                    Messages.text("error", e.getMessage()),
                    Messages.text("types", String.join(", ", ActionParser.typeNames()))));
            return;
        }
        List<Object> entries = new ArrayList<>(npc.actions(trigger).entries());
        if (place != Place.END && number > entries.size() + (place == Place.REPLACE ? 0 : 1)) {
            noSuchAction(sender, trigger, number, entries.size());
            return;
        }
        switch (place) {
            case END -> entries.add(line);
            case BEFORE -> entries.add(number - 1, line);
            case AFTER -> entries.add(Math.min(number, entries.size()), line);
            case REPLACE -> entries.set(number - 1, line);
        }
        support.npcs.setActions(npc, trigger, entries);
        sender.sendMessage(support.text.message(MessageKey.NPC_ACTION_ADDED, sender, Messages.text("name", npc.name()),
                Messages.text("trigger", trigger.commandName()), Messages.text("action", line)));
    }

    private void remove(CommandSender sender, String npcName, String triggerName, int number) {
        NpcData npc = support.found(sender, npcName);
        NpcTrigger trigger = NpcTrigger.fromCommandName(triggerName);
        if (npc == null || trigger == null) {
            return;
        }
        List<Object> entries = new ArrayList<>(npc.actions(trigger).entries());
        if (number > entries.size()) {
            noSuchAction(sender, trigger, number, entries.size());
            return;
        }
        entries.remove(number - 1);
        support.npcs.setActions(npc, trigger, entries);
        changed(sender, npc, trigger);
    }

    private void move(CommandSender sender, String npcName, String triggerName, int number, int direction) {
        NpcData npc = support.found(sender, npcName);
        NpcTrigger trigger = NpcTrigger.fromCommandName(triggerName);
        if (npc == null || trigger == null) {
            return;
        }
        List<Object> entries = new ArrayList<>(npc.actions(trigger).entries());
        int from = number - 1;
        int to = from + direction;
        if (number > entries.size() || to < 0 || to >= entries.size()) {
            noSuchAction(sender, trigger, number, entries.size());
            return;
        }
        Collections.swap(entries, from, to);
        support.npcs.setActions(npc, trigger, entries);
        changed(sender, npc, trigger);
    }

    private void clear(CommandSender sender, String npcName, String triggerName) {
        NpcData npc = support.found(sender, npcName);
        NpcTrigger trigger = NpcTrigger.fromCommandName(triggerName);
        if (npc == null || trigger == null) {
            return;
        }
        support.npcs.setActions(npc, trigger, List.of());
        sender.sendMessage(support.text.message(MessageKey.NPC_ACTIONS_CLEARED, sender,
                Messages.text("name", npc.name()), Messages.text("trigger", trigger.commandName())));
    }

    private void list(CommandSender sender, String npcName, String triggerName) {
        NpcData npc = support.found(sender, npcName);
        NpcTrigger trigger = NpcTrigger.fromCommandName(triggerName);
        if (npc == null || trigger == null) {
            return;
        }
        List<Object> entries = npc.actions(trigger).entries();
        List<String> numbered = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            numbered.add((i + 1) + ". " + ActionEntries.describe(entries.get(i)));
        }
        sender.sendMessage(support.text.message(MessageKey.NPC_ACTION_LIST, sender, Messages.text("name", npc.name()),
                Messages.text("trigger", trigger.commandName()),
                Messages.text("actions", numbered.isEmpty() ? "none" : String.join(" | ", numbered))));
    }

    private void changed(CommandSender sender, NpcData npc, NpcTrigger trigger) {
        sender.sendMessage(support.text.message(MessageKey.NPC_ACTION_CHANGED, sender,
                Messages.text("name", npc.name()), Messages.text("trigger", trigger.commandName())));
    }

    private void noSuchAction(CommandSender sender, NpcTrigger trigger, int number, int count) {
        sender.sendMessage(support.text.message(MessageKey.NPC_NO_SUCH_ACTION, sender,
                Messages.text("number", number), Messages.text("trigger", trigger.commandName()),
                Messages.text("count", count)));
    }
}
