package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Runs one of its actions, chosen at random. Written as a list:
 * <pre>
 * - random:
 *     - "message: Heads!"
 *     - "message: Tails!"
 * </pre>
 */
public record RandomAction(List<Action> options) implements Action {

    public RandomAction {
        options = List.copyOf(options);
    }

    @Override
    public Step run(ActionContext context) {
        if (options.isEmpty()) {
            return Step.CONTINUE;
        }
        return options.get(ThreadLocalRandom.current().nextInt(options.size())).run(context);
    }

    @Override
    public String describe() {
        return "random: [" + options.stream().map(Action::describe).collect(Collectors.joining(", ")) + "]";
    }
}
