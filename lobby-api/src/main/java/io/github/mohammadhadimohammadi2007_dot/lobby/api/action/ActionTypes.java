package io.github.mohammadhadimohammadi2007_dot.lobby.api.action;

/**
 * New action types, usable everywhere actions are: NPCs, holograms, menus, portals and hotbar items. An
 * action is written {@code "type: argument"} in configs; for example after
 * <pre>{@code
 * context.actionTypes().register("give_coins", argument -> {
 *     int amount = Integer.parseInt(argument);   // checked once, when the config is read
 *     return run -> { coins.add(run.player(), amount); return ActionResult.CONTINUE; };
 * });
 * }</pre>
 * an NPC can have the action {@code give_coins: 10}.
 *
 * <p>Register in {@code onLoad}, before the lobby reads its files; registering later throws
 * {@link IllegalStateException}. When the addon is disabled its types are removed, and entries using them
 * are reported as unknown (and skipped) from the next reload.
 */
public interface ActionTypes {

    /**
     * Adds an action type.
     *
     * @param type    lowercase letters, digits and {@code _}; prefixing it with the addon's name avoids clashes
     * @param factory reads the argument of each entry
     * @throws IllegalArgumentException if the name is invalid or already taken (built-in or another addon)
     */
    void register(String type, ActionFactory factory);
}
