package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/** A player clicked an NPC. Cancelling it stops the NPC's actions. Tick thread. */
public final class NpcClickEvent extends CancellableLobbyEvent {

    private final String npc;
    private final ClickType click;

    public NpcClickEvent(Player player, String npc, ClickType click) {
        super(player);
        this.npc = npc;
        this.click = click;
    }

    /** The NPC's name, as in {@code /npc}. */
    public String npc() {
        return npc;
    }

    public ClickType click() {
        return click;
    }
}
