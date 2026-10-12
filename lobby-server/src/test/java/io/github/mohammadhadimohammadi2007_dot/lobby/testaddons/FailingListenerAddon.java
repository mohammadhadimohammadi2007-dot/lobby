package io.github.mohammadhadimohammadi2007_dot.lobby.testaddons;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.LobbyChatEvent;

/** Throws from a chat listener. */
public final class FailingListenerAddon implements LobbyAddon {

    @Override
    public void onEnable(AddonContext context) {
        context.events().listen(LobbyChatEvent.class, event -> {
            throw new IllegalStateException("listener broken on purpose");
        });
    }
}
