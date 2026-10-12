package io.github.mohammadhadimohammadi2007_dot.lobby.testaddons;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.ActionResult;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.NpcClickEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.MenuButton;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.MenuView;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder.PlaceholderValue;
import net.minestom.server.command.builder.Command;
import net.minestom.server.item.Material;

import java.time.Duration;
import java.util.List;

/** Registers one of everything, and records what happens in system properties ({@code recording.*}). */
public final class RecordingAddon implements LobbyAddon {

    @Override
    public void onLoad(AddonContext context) {
        System.setProperty("recording.loaded", "true");
        context.placeholders().register("recording", params -> switch (params) {
            case "constant" -> PlaceholderValue.constant("fixed");
            case "name" -> PlaceholderValue.perPlayer(Duration.ofSeconds(1), player -> "hi " + player.getUsername());
            default -> null;
        });
        context.actionTypes().register("recording_mark", argument -> run -> {
            System.setProperty("recording.mark", run.fill(argument));
            return ActionResult.CONTINUE;
        });
        try {
            Class.forName("io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServer", false,
                    getClass().getClassLoader());
            System.setProperty("recording.internal", "visible");
        } catch (ClassNotFoundException e) {
            System.setProperty("recording.internal", "hidden");
        }
        System.setProperty("recording.config", context.config().getString("greeting", "missing"));
    }

    @Override
    public void onEnable(AddonContext context) {
        Command command = new Command("recording");
        command.setDefaultExecutor((sender, ignored) -> System.setProperty("recording.command", "ran"));
        context.commands().register(command);
        context.events().listen(NpcClickEvent.class, event -> {
            System.setProperty("recording.npc", event.npc() + " " + event.click());
            if (event.npc().equals("blocked")) {
                event.setCancelled(true);
            }
        });
        context.scheduler().repeat(Duration.ofSeconds(60), () -> { });
        context.menus().register("recording-menu", player -> new MenuView("Recording", 1, List.of(
                MenuButton.of(4, Material.DIAMOND, "Click", List.of(), clicker ->
                        System.setProperty("recording.menu", clicker.getUsername())))));
        System.setProperty("recording.enabled", "true");
    }

    @Override
    public void onDisable(AddonContext context) {
        System.setProperty("recording.disabled", "true");
    }
}
