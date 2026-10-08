package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * A {@code show-if:} line of a menu item, so one slot can hold different items for different players
 * without any code. Two forms:
 *
 * <ul>
 *   <li>{@code "permission: lobby.vip"} - the viewer must have it.</li>
 *   <li>{@code "%server_status_bedwars% == offline"} - compare two texts, with placeholders filled in.
 *       Operators: {@code ==}, {@code !=}, {@code >}, {@code <}, {@code >=}, {@code <=} and
 *       {@code contains}. Text comparison ignores capitals; numbers are compared as numbers.</li>
 * </ul>
 */
public sealed interface MenuCondition {

    /** True if the item should be shown to this viewer. */
    boolean test(Player viewer, PlaceholderService placeholders, PermissionService permissions);

    /** As written in the config. */
    String describe();

    /** Thrown for a line that cannot be read. */
    final class ConditionException extends Exception {
        ConditionException(String message) {
            super(message);
        }
    }

    record HasPermission(String permission) implements MenuCondition {
        @Override
        public boolean test(Player viewer, PlaceholderService placeholders, PermissionService permissions) {
            return permissions.hasPermission(viewer, permission);
        }

        @Override
        public String describe() {
            return "permission: " + permission;
        }
    }

    record Compare(String left, Operator operator, String right) implements MenuCondition {
        @Override
        public boolean test(Player viewer, PlaceholderService placeholders, PermissionService permissions) {
            String a = placeholders.plainText(left, viewer).strip();
            String b = placeholders.plainText(right, viewer).strip();
            Double first = number(a);
            Double second = number(b);
            if (first != null && second != null) {
                return operator.compare(Double.compare(first, second), a, b);
            }
            return operator.compare(a.compareToIgnoreCase(b), a, b);
        }

        @Override
        public String describe() {
            return left + " " + operator.symbol + " " + right;
        }

        private static Double number(String text) {
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** The comparisons a condition can make. */
    enum Operator {
        EQUAL("=="),
        NOT_EQUAL("!="),
        GREATER(">"),
        LESS("<"),
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        CONTAINS("contains");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        boolean compare(int comparison, String left, String right) {
            return switch (this) {
                case EQUAL -> comparison == 0;
                case NOT_EQUAL -> comparison != 0;
                case GREATER -> comparison > 0;
                case LESS -> comparison < 0;
                case GREATER_OR_EQUAL -> comparison >= 0;
                case LESS_OR_EQUAL -> comparison <= 0;
                case CONTAINS -> left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
            };
        }
    }

    /** Reads one line. */
    static MenuCondition parse(String line) throws ConditionException {
        String trimmed = line.strip();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("permission:")) {
            String permission = trimmed.substring("permission:".length()).strip();
            if (permission.isEmpty()) {
                throw new ConditionException("missing permission after 'permission:'");
            }
            return new HasPermission(permission);
        }
        // Longest symbols first, so ">=" is not read as ">".
        for (Operator operator : List.of(Operator.GREATER_OR_EQUAL, Operator.LESS_OR_EQUAL, Operator.EQUAL,
                Operator.NOT_EQUAL, Operator.CONTAINS, Operator.GREATER, Operator.LESS)) {
            String symbol = operator == Operator.CONTAINS ? " contains " : operator.symbol;
            int at = trimmed.indexOf(symbol);
            if (at > 0) {
                String left = trimmed.substring(0, at).strip();
                String right = trimmed.substring(at + symbol.length()).strip();
                if (left.isEmpty() || right.isEmpty()) {
                    throw new ConditionException("'" + trimmed + "' needs a value on both sides of '"
                            + operator.symbol + "'");
                }
                return new Compare(left, operator, right);
            }
        }
        throw new ConditionException("'" + trimmed + "' is not a condition. Use \"permission: <node>\""
                + " or a comparison like \"%server_online% > 10\"");
    }
}
