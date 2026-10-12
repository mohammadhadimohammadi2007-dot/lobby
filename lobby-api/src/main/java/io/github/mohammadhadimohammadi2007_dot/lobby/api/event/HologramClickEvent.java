package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/** A player clicked a hologram. Cancelling it stops the hologram's actions. Tick thread. */
public final class HologramClickEvent extends CancellableLobbyEvent {

    private final String hologram;
    private final ClickType click;

    public HologramClickEvent(Player player, String hologram, ClickType click) {
        super(player);
        this.hologram = hologram;
        this.click = click;
    }

    /** The hologram's name, as in {@code /hologram}. */
    public String hologram() {
        return hologram;
    }

    public ClickType click() {
        return click;
    }
}
