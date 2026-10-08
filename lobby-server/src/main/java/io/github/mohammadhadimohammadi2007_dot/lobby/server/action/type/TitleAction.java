package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import net.kyori.adventure.title.Title;
import net.minestom.server.entity.Player;

import java.time.Duration;

/**
 * {@code title: <title> | <subtitle> | <fade in> <stay> <fade out>}: the subtitle and the times (in ticks)
 * are optional, e.g. {@code "title: <gold>Welcome | <gray>to the lobby | 10 60 20"}.
 */
public record TitleAction(String title, String subtitle, int fadeIn, int stay, int fadeOut) implements Action {

    private static final long MILLIS_PER_TICK = 50;

    @Override
    public Step run(ActionContext context) {
        Player player = context.player();
        Title.Times times = Title.Times.times(ticks(fadeIn), ticks(stay), ticks(fadeOut));
        player.showTitle(Title.title(context.services().text().render(title, player),
                context.services().text().render(subtitle, player), times));
        return Step.CONTINUE;
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * MILLIS_PER_TICK);
    }

    @Override
    public String describe() {
        return "title: " + title + " | " + subtitle + " | " + fadeIn + " " + stay + " " + fadeOut;
    }
}
