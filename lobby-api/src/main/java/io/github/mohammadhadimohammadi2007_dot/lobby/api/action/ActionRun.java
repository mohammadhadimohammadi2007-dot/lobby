package io.github.mohammadhadimohammadi2007_dot.lobby.api.action;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;
import net.minestom.server.entity.Player;

/** One run of an action: who for, and what started it. */
@NonExtendable
public interface ActionRun {

    /** The player who clicked, walked in or used an item. */
    Player player();

    /** What started it, for log messages, e.g. {@code npc 'guide'} or {@code menu 'servers'}. */
    String source();

    /** {@code text} with the placeholders filled in for {@link #player()}, as plain text. */
    String fill(String text);
}
