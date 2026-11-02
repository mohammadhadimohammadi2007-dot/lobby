package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

/**
 * When a listener runs, compared with other addons' listeners for the same event. Listeners of the same priority run
 * in the order the addons were enabled.
 */
public enum EventPriority {
    /** Before the others: to change or cancel an event first. */
    EARLY,
    /** The default. */
    NORMAL,
    /** After the others: to have the last word on a change or cancellation. */
    LATE,
    /**
     * Last, to see the final outcome (whether the event ended up cancelled, the final chat message). Do not change
     * the event here.
     */
    MONITOR
}
