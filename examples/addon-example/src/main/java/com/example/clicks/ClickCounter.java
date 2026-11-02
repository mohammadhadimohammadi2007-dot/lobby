package com.example.clicks;

import java.util.UUID;

/**
 * What this addon offers other addons, through {@code context.services()}. An addon that wants it puts
 * {@code "depends": ["example"]} in its addon.json (so it sees this interface) and asks:
 * <pre>{@code
 * context.services().get(ClickCounter.class).ifPresent(counter -> counter.clicks(player.getUuid()));
 * }</pre>
 */
public interface ClickCounter {

    /** How often the player clicked an NPC (0 until their count is loaded after they join). */
    int clicks(UUID player);
}
