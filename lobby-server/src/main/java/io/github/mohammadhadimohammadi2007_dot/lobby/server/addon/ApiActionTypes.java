package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.ActionFactory;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.ActionResult;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.ActionRun;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.ActionTypes;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.CustomAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.InvalidActionException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import net.minestom.server.entity.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** An addon's action types, plugged into the lobby's {@link ActionParser}. */
final class ApiActionTypes implements ActionTypes {

    private final AddonContextImpl context;
    private final PlaceholderService placeholders;
    private final List<String> types = new CopyOnWriteArrayList<>();

    ApiActionTypes(AddonContextImpl context, PlaceholderService placeholders) {
        this.context = context;
        this.placeholders = placeholders;
    }

    @Override
    public void register(String type, ActionFactory factory) {
        if (!context.loading()) {
            throw new IllegalStateException("Register action types in onLoad, before the lobby reads its files"
                    + " (type '" + type + "')");
        }
        ActionParser.registerCustom(type, argument -> create(type, argument, factory));
        types.add(type);
    }

    private Action create(String type, String argument, ActionFactory factory) throws ActionParser.ActionException {
        if (!context.running()) {
            throw new ActionParser.ActionException("the addon of '" + type + "' is not running");
        }
        CustomAction action;
        try {
            action = factory.create(argument);
        } catch (InvalidActionException e) {
            throw new ActionParser.ActionException(e.getMessage());
        } catch (RuntimeException | LinkageError e) {
            context.fail("reading the action '" + type + ": " + argument + "'", e);
            throw new ActionParser.ActionException("the addon of '" + type + "' failed to read it");
        }
        if (action == null) {
            throw new ActionParser.ActionException("the addon of '" + type + "' could not read it");
        }
        return new AddonAction(type, argument, action);
    }

    int count() {
        return types.size();
    }

    void cleanup() {
        types.forEach(ActionParser::unregisterCustom);
        types.clear();
    }

    /** One entry of an addon's type in an action list. */
    private final class AddonAction implements Action {

        private final String type;
        private final String argument;
        private final CustomAction action;

        AddonAction(String type, String argument, CustomAction action) {
            this.type = type;
            this.argument = argument;
            this.action = action;
        }

        @Override
        public Step run(ActionContext run) {
            if (!context.running()) {
                // The addon was disabled after this list was read; the next reload drops the entry.
                return Step.CONTINUE;
            }
            ActionResult result;
            try {
                result = action.run(new ActionRun() {
                    @Override
                    public Player player() {
                        return run.player();
                    }

                    @Override
                    public String source() {
                        return run.source();
                    }

                    @Override
                    public String fill(String text) {
                        return placeholders.plainText(text, run.player());
                    }
                });
            } catch (RuntimeException | LinkageError e) {
                context.fail("the action '" + describe() + "' of " + run.source(), e);
                return Step.STOP;
            }
            if (result == null) {
                return Step.CONTINUE;
            }
            return new Step(result.stop(), result.waitTicks());
        }

        @Override
        public String describe() {
            return argument.isEmpty() ? type : type + ": " + argument;
        }
    }
}
