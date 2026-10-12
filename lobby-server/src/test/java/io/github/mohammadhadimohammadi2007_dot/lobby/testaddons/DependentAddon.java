package io.github.mohammadhadimohammadi2007_dot.lobby.testaddons;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;

/** Depends on another addon; records the order it was enabled in. */
public final class DependentAddon implements LobbyAddon {

    @Override
    public void onEnable(AddonContext context) {
        String before = System.getProperty("order", "");
        System.setProperty("order", before + context.description().id() + ";");
    }
}
