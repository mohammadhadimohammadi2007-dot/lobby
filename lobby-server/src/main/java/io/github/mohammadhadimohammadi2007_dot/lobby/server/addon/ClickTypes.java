package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.ClickType;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectClicks;

/** The lobby's click types in the API's words. */
public final class ClickTypes {

    private ClickTypes() {
    }

    public static ClickType toApi(ClientObjectClicks.ClickType type) {
        return type == ClientObjectClicks.ClickType.LEFT ? ClickType.LEFT : ClickType.RIGHT;
    }
}
