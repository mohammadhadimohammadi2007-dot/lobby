package io.github.mohammadhadimohammadi2007_dot.lobby.testaddons;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.LobbyChatEvent;

/** Changes "secret" in chat to "*****" and cancels messages saying "forbidden". */
public final class ChatChangingAddon implements LobbyAddon {

    @Override
    public void onEnable(AddonContext context) {
        context.events().listen(LobbyChatEvent.class, event -> {
            if (event.message().contains("forbidden")) {
                event.cancel(null);
            } else {
                event.message(event.message().replace("secret", "*****"));
            }
        });
    }
}
