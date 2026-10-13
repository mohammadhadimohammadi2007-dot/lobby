package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A player died in the PvP area: they were sent back to the area's spawn with a new kit, and the records
 * are updated. Tick thread.
 */
public final class PvpKillEvent extends LobbyPlayerEvent {

    private final @Nullable Player killer;
    private final boolean combatLog;

    /** @param player the player who died */
    public PvpKillEvent(Player player, @Nullable Player killer, boolean combatLog) {
        super(player);
        this.killer = killer;
        this.combatLog = combatLog;
    }

    /** Who made the last hit, or {@code null} if nobody did. */
    public @Nullable Player killer() {
        return killer;
    }

    /** True if the player left the game while in combat (counted as a death); they are offline now. */
    public boolean combatLog() {
        return combatLog;
    }
}
