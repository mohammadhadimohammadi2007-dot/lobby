package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

/**
 * One step of an action list, written in configs as {@code "type: argument"}, for example
 * {@code "connect_group: bedwars"}. Used by NPCs, holograms, menus, hotbar items and portals.
 *
 * <p>Actions run on a background thread so placeholders never slow the server tick. Anything that touches
 * the world (teleports, commands) hands itself to the tick thread.
 */
public interface Action {

    /** Runs the action for {@code context.player()} and says how the list continues. */
    Step run(ActionContext context);

    /** The action as it would be written in a config, for {@code /npc action ... list} and data files. */
    String describe();

    /**
     * What happens after an action.
     *
     * @param stop       true to end the list here (for example a missing permission)
     * @param waitTicks  ticks to wait before the next action (0 = go on right away)
     */
    record Step(boolean stop, int waitTicks) {
        public static final Step CONTINUE = new Step(false, 0);
        public static final Step STOP = new Step(true, 0);

        public static Step waitTicks(int ticks) {
            return new Step(false, ticks);
        }
    }
}
