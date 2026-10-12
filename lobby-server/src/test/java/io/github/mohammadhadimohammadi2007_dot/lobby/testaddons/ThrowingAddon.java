package io.github.mohammadhadimohammadi2007_dot.lobby.testaddons;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;

/** Fails in onEnable after registering a placeholder in onLoad. */
public final class ThrowingAddon implements LobbyAddon {

    @Override
    public void onLoad(AddonContext context) {
        context.placeholders().register("throwing", params -> null);
    }

    @Override
    public void onEnable(AddonContext context) {
        throw new IllegalStateException("broken on purpose");
    }
}
