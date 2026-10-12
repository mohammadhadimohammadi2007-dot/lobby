package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

/** Where an addon stands. */
public enum AddonState {
    /** Found and its {@code onLoad} ran; waiting for the lobby to start. */
    LOADED,
    /** Running. */
    ENABLED,
    /** Stopped on purpose (shutdown or reload). */
    DISABLED,
    /** Threw an error while loading, enabling or in a listener, and was stopped. */
    FAILED,
    /** Its jar or addon.json cannot be read. */
    INVALID,
    /** Written for another API version. */
    INCOMPATIBLE,
    /** An addon it depends on is missing or did not start. */
    MISSING_DEPENDENCY,
    /** It depends on itself through other addons. */
    DEPENDENCY_CYCLE;

    /** True for the states where the addon's code is not running because something is wrong. */
    public boolean problem() {
        return this != LOADED && this != ENABLED && this != DISABLED;
    }
}
