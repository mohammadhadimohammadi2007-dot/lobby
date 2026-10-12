package io.github.mohammadhadimohammadi2007_dot.lobby.api;

/**
 * The main class of an addon, named by {@code main} in its {@code addon.json}. It needs a public constructor
 * without parameters.
 *
 * <p>The lobby calls, in this order:
 * <ol>
 *   <li>{@link #onLoad} for every addon, before the lobby reads its config and data files. Register action
 *       types and placeholders here, so NPCs, menus and holograms can use them as soon as they are read.</li>
 *   <li>{@link #onEnable} for every addon, once the lobby is running: commands, events, NPCs, menus and every
 *       other service are available from here on.</li>
 *   <li>{@link #onDisable} on shutdown, on {@code /setup-lobby addons reload <id>}, or when the addon failed.</li>
 * </ol>
 * Addons are loaded and enabled after the addons they depend on, and disabled before them. These methods never
 * run on the tick thread: hand changes to players and the world to {@code context.scheduler().nextTick(...)}.
 *
 * <p>An exception thrown from any of these methods (or from an event listener) disables the addon with an
 * error in the log; the lobby and the other addons go on. Everything the addon registered through its
 * {@link AddonContext} (commands, listeners, tasks, placeholders, action types, menus) is removed when it is
 * disabled, so {@link #onDisable} only has to clean up what the addon made by itself.
 */
public interface LobbyAddon {

    /** Called before the lobby reads its files. Only the registries and the addon's own files are usable. */
    default void onLoad(AddonContext context) throws Exception {
    }

    /** Called once the lobby is running. */
    void onEnable(AddonContext context) throws Exception;

    /** Called when the addon stops. Also called after a failed {@link #onEnable}, so keep it null-safe. */
    default void onDisable(AddonContext context) throws Exception {
    }
}
