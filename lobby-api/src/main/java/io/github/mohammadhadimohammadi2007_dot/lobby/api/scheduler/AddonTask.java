package io.github.mohammadhadimohammadi2007_dot.lobby.api.scheduler;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;

/** A task waiting or repeating. */
@NonExtendable
public interface AddonTask {

    /** Stops it; a run in progress finishes. */
    void cancel();

    boolean cancelled();
}
