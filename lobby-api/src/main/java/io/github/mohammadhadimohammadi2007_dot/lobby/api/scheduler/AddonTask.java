package io.github.mohammadhadimohammadi2007_dot.lobby.api.scheduler;

/** A task waiting or repeating. */
public interface AddonTask {

    /** Stops it; a run in progress finishes. */
    void cancel();

    boolean cancelled();
}
