package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

import java.util.List;

/**
 * Shows checks: one line each, colored by status. In game a fixing command is a click that puts it in the
 * chat box; in the console it is written out on the next line.
 */
public final class CheckPrinter {

    private CheckPrinter() {
    }

    public static void print(CommandSender sender, List<Check> checks) {
        boolean inGame = sender instanceof Player;
        for (Check check : checks) {
            Component line = Component.text(check.status().symbol() + " ", color(check.status()))
                    .append(Component.text(check.title(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(": " + check.detail(), NamedTextColor.GRAY));
            if (check.command() != null && inGame) {
                line = line.append(Component.text(" [" + check.command().strip() + "]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand(check.command()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to put this in the chat box"))));
            }
            sender.sendMessage(line);
            if (check.command() != null && !inGame) {
                // A command that still needs a name ("/op ") is shown with a placeholder.
                String command = check.command().replaceFirst("^/", "");
                sender.sendMessage(Component.text("    -> " + (command.endsWith(" ") ? command + "<name>" : command),
                        NamedTextColor.AQUA));
            }
        }
    }

    /** "3 done, 1 to fix, 2 to look at". */
    public static String summary(List<Check> checks) {
        long ok = checks.stream().filter(c -> c.status() == Check.Status.OK).count();
        long fail = checks.stream().filter(c -> c.status() == Check.Status.FAIL).count();
        long warn = checks.stream().filter(c -> c.status() == Check.Status.WARN).count();
        return ok + " done, " + fail + " to fix, " + warn + " to look at";
    }

    private static TextColor color(Check.Status status) {
        return switch (status) {
            case OK -> NamedTextColor.GREEN;
            case WARN -> NamedTextColor.YELLOW;
            case FAIL -> NamedTextColor.RED;
            case INFO -> NamedTextColor.DARK_GRAY;
        };
    }
}
