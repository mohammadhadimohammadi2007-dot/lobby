package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** What the {@code /npc} subcommands share: finding an NPC, its name argument and the replies. */
final class NpcCommandSupport {

    /** Something to do with an NPC that exists. */
    interface OnNpc {
        void run(CommandSender sender, NpcData npc);
    }

    final NpcService npcs;
    final LobbyText text;

    NpcCommandSupport(NpcService npcs, LobbyText text) {
        this.npcs = npcs;
        this.text = text;
    }

    /** An argument for an NPC name that suggests the NPCs that exist. */
    ArgumentWord npcArgument() {
        ArgumentWord argument = ArgumentType.Word("npc");
        argument.setSuggestionCallback((sender, context, suggestion) -> {
            for (NpcData npc : npcs.all()) {
                suggestion.addEntry(new SuggestionEntry(npc.name()));
            }
        });
        return argument;
    }

    /** A subcommand whose only argument is an NPC. */
    Command withNpc(String name, ArgumentWord npc, OnNpc action, String... aliases) {
        Command command = new Command(name, aliases);
        command.setDefaultExecutor((sender, context) -> usage(sender));
        command.addSyntax((sender, context) -> {
            NpcData data = found(sender, context.get(npc));
            if (data != null) {
                action.run(sender, data);
            }
        }, npc);
        return command;
    }

    /** The NPC with that name, or {@code null} after telling the sender it does not exist. */
    @Nullable NpcData found(CommandSender sender, String name) {
        NpcData data = npcs.get(name);
        if (data == null) {
            sender.sendMessage(text.message(MessageKey.NPC_UNKNOWN, sender, Messages.text("name", name)));
        }
        return data;
    }

    void usage(CommandSender sender) {
        sender.sendMessage(text.message(MessageKey.NPC_USAGE, sender));
    }

    /** Saves a change and says what it was. */
    void changed(CommandSender sender, NpcData npc, String property, Object value) {
        npcs.changed(npc);
        sender.sendMessage(text.message(MessageKey.NPC_SET, sender, Messages.text("name", npc.name()),
                Messages.text("property", property), Messages.text("value", value)));
    }

    void invalid(CommandSender sender, String property, String error, String allowed) {
        sender.sendMessage(text.message(MessageKey.NPC_INVALID_VALUE, sender, Messages.text("property", property),
                Messages.text("error", error), Messages.text("allowed", allowed)));
    }

    /**
     * {@code true}/{@code false}, or {@code toggle} (or nothing) to flip the current value, as FancyNpcs
     * does. {@code null} for anything else.
     */
    static @Nullable Boolean state(@Nullable String value, boolean current) {
        if (value == null) {
            return !current;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "enabled" -> true;
            case "false", "off", "no", "disabled" -> false;
            case "toggle" -> !current;
            default -> null;
        };
    }
}
