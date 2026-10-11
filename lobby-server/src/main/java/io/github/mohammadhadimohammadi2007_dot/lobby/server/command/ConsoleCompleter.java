package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import net.minestom.server.command.CommandManager;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandSyntax;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.Suggestion;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.listener.TabCompleteListener;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tab completion in the console, from the real command tree: command names, subcommands, fixed words
 * ({@code /npc pose <npc> <crouching|sitting...>}) and whatever a command suggests itself (NPC names...).
 */
final class ConsoleCompleter implements Completer {

    private final CommandManager commands;
    private final CommandSender console;

    ConsoleCompleter(CommandManager commands) {
        this.commands = commands;
        this.console = commands.getConsoleSender();
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        List<String> words = line.words().subList(0, line.wordIndex() + 1);
        String upToCursor = line.line().substring(0, line.cursor());
        for (String suggestion : suggestions(words, upToCursor)) {
            candidates.add(new Candidate(suggestion));
        }
    }

    /**
     * What may come at the last word of {@code words} (which may be empty or partly typed); JLine keeps
     * those that start with what was typed.
     *
     * @param upToCursor the line up to the cursor, for the commands' own suggestions
     */
    List<String> suggestions(List<String> words, String upToCursor) {
        Set<String> result = new LinkedHashSet<>();
        if (words.size() <= 1) {
            for (Command command : commands.getCommands()) {
                result.add(command.getName());
                result.addAll(List.of(command.getAliases()));
            }
            return List.copyOf(result);
        }
        Command current = commands.getCommand(words.getFirst().toLowerCase(Locale.ROOT));
        if (current == null) {
            return List.of();
        }
        // Follow the typed subcommands as far as they go.
        int position = 1;
        while (position < words.size() - 1) {
            Command sub = subcommand(current, words.get(position));
            if (sub == null) {
                break;
            }
            current = sub;
            position++;
        }
        int argumentIndex = words.size() - 1 - position;
        if (argumentIndex == 0) {
            for (Command sub : current.getSubcommands()) {
                result.add(sub.getName());
                result.addAll(List.of(sub.getAliases()));
            }
        }
        for (CommandSyntax syntax : current.getSyntaxes()) {
            Argument<?>[] arguments = syntax.getArguments();
            if (argumentIndex < arguments.length && matchesSoFar(arguments, words, position, argumentIndex)) {
                result.addAll(fixedWords(arguments[argumentIndex]));
            }
        }
        Suggestion suggestion;
        try {
            suggestion = TabCompleteListener.getSuggestion(console, upToCursor);
        } catch (RuntimeException e) {
            // A command's own suggestions failed; the fixed words above still help.
            suggestion = null;
        }
        if (suggestion != null) {
            for (SuggestionEntry entry : suggestion.getEntries()) {
                result.add(entry.getEntry());
            }
        }
        return List.copyOf(result);
    }

    private static Command subcommand(Command parent, String name) {
        for (Command sub : parent.getSubcommands()) {
            if (sub.getName().equalsIgnoreCase(name) || List.of(sub.getAliases()).stream().anyMatch(name::equalsIgnoreCase)) {
                return sub;
            }
        }
        return null;
    }

    /** True if the fixed words typed so far fit this syntax (so {@code add} is not offered after {@code remove}). */
    private static boolean matchesSoFar(Argument<?>[] arguments, List<String> words, int position, int count) {
        for (int i = 0; i < count; i++) {
            List<String> fixed = fixedWords(arguments[i]);
            if (!fixed.isEmpty() && fixed.stream().noneMatch(words.get(position + i)::equalsIgnoreCase)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> fixedWords(Argument<?> argument) {
        if (argument instanceof ArgumentLiteral literal) {
            return List.of(literal.getId());
        }
        if (argument instanceof ArgumentWord word && word.hasRestrictions()) {
            return new ArrayList<>(List.of(word.getRestrictions()));
        }
        return List.of();
    }
}
