package io.github.mohammadhadimohammadi2007_dot.lobby.testaddons;

import com.example.clicks.ClickCounter;
import com.example.clicks.ClickMilestoneEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.EventPriority;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Regions;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An addon that depends on the example addon: uses its {@link ClickCounter} service, listens to its own event at
 * every priority, and records what it sees in system properties ({@code user.*}).
 */
public final class ExampleUserAddon implements LobbyAddon {

    @Override
    public void onLoad(AddonContext context) {
        // The example provides its service in its onLoad, which runs first.
        System.setProperty("user.provider", context.services().provider(ClickCounter.class).orElse("none"));
        try {
            context.services().provide(ClickCounter.class, player -> 0);
            System.setProperty("user.steal", "allowed");
        } catch (IllegalArgumentException e) {
            System.setProperty("user.steal", "refused");
        }
    }

    @Override
    public void onEnable(AddonContext context) {
        // Registered from last to first: the priorities decide the order, not the registration.
        context.events().listen(ClickMilestoneEvent.class, EventPriority.LATE, event -> record("late"));
        context.events().listen(ClickMilestoneEvent.class, event -> record("normal"));
        context.events().listen(ClickMilestoneEvent.class, EventPriority.EARLY, event -> {
            record("early");
            UUID player = event.getPlayer().getUuid();
            System.setProperty("user.counted", String.valueOf(context.services().get(ClickCounter.class)
                    .map(counter -> counter.clicks(player)).orElse(-1)));
            System.setProperty("user.regions", context.regions().at(event.getPlayer()).stream()
                    .map(region -> region.owner() + "/" + region.name()).collect(Collectors.joining(",")));
        });
        System.setProperty("user.own", String.valueOf(context.regions().own().stream().map(Regions.Region::name).toList()));
    }

    private static void record(String what) {
        String before = System.getProperty("user.order", "");
        System.setProperty("user.order", before.isEmpty() ? what : before + "," + what);
    }
}
