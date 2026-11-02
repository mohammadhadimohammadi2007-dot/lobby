package com.example.clicks;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.ActionResult;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.InvalidActionException;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.config.AddonConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.EventPriority;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.LobbyJoinEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.LobbyQuitEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.NpcClickEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.RegionEnterEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.MenuButton;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.MenuView;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder.PlaceholderValue;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts how often each player clicks an NPC. Small, but it touches most of the API:
 * <ul>
 *   <li>a config file with defaults from the jar ({@code config.yml});</li>
 *   <li>a placeholder, {@code %example_clicks%}, usable in holograms, the scoreboard, menus...;</li>
 *   <li>an action type, {@code example_reset}, usable on NPCs, menus, portals and hotbar items;</li>
 *   <li>events: NPC clicks, joins and quits, a region, and an event of its own with a priority;</li>
 *   <li>values saved per player ({@code playerSettings()}), loaded and saved off the tick thread;</li>
 *   <li>a command, {@code /clicks}, and a menu built in code;</li>
 *   <li>a region of its own that greets players, and messages in the lobby's style ({@code texts()});</li>
 *   <li>a service other addons can use ({@link ClickCounter}).</li>
 * </ul>
 */
public final class ClickCounterAddon implements LobbyAddon, ClickCounter {

    /** The key of the saved count in this addon's player settings. */
    private static final String CLICKS_KEY = "clicks";
    private static final String ZONE = "zone";

    /** Counts of the players online, loaded when they join. Events and placeholders run on different threads. */
    private final Map<UUID, Integer> clicks = new ConcurrentHashMap<>();
    private AddonContext context;

    @Override
    public void onLoad(AddonContext context) {
        this.context = context;
        // Placeholders and action types are registered in onLoad: the lobby reads its files right after, and
        // holograms, NPCs and menus that use them must find them.
        context.placeholders().register("example", params -> switch (params) {
            // Per player and cached for a second: the scoreboard asks often, this keeps it cheap.
            case "clicks" -> PlaceholderValue.perPlayer(Duration.ofSeconds(1),
                    player -> String.valueOf(clicks(player.getUuid())));
            default -> null;
        });
        context.actionTypes().register("example_reset", argument -> {
            // The argument is checked once, when the lobby reads the file, not on every click.
            if (!argument.isEmpty()) {
                throw new InvalidActionException("example_reset takes no argument");
            }
            return run -> {
                setClicks(run.player(), 0);
                return ActionResult.CONTINUE;
            };
        });
        // Other addons may use the counter from their own onLoad on.
        context.services().provide(ClickCounter.class, this);
    }

    @Override
    public void onEnable(AddonContext context) {
        AddonConfig config = context.config();
        int milestone = Math.max(1, config.getInt("milestone", 10));
        String milestoneMessage = config.getString("milestone-message", "<prefix>Click number <clicks>!");

        context.events().listen(LobbyJoinEvent.class, event -> load(event.getPlayer()));
        context.events().listen(LobbyQuitEvent.class, event -> clicks.remove(event.getPlayer().getUuid()));
        context.events().listen(NpcClickEvent.class, event -> {
            int now = setClicks(event.getPlayer(), clicks(event.getPlayer().getUuid()) + 1);
            if (now % milestone == 0) {
                // Our own event: other addons can react, or cancel nothing (it is not cancellable).
                context.events().fire(new ClickMilestoneEvent(event.getPlayer(), now));
            }
        });
        // MONITOR runs after every other listener: the place to look at the final outcome.
        context.events().listen(ClickMilestoneEvent.class, EventPriority.MONITOR, event ->
                context.texts().send(event.getPlayer(), milestoneMessage.replace("<clicks>", String.valueOf(event.clicks()))));

        Command command = new Command("clicks");
        command.setDefaultExecutor((sender, ignored) -> {
            if (sender instanceof Player player) {
                context.menus().open(player, "example-clicks");
            }
        });
        // commands.yml decides who sees /clicks: add it to a group there (docs/commands.md).
        context.commands().register(command);

        context.menus().register("example-clicks", player -> new MenuView("<dark_aqua>Your clicks", 3, List.of(
                MenuButton.withActions(13, Material.NETHER_STAR, "<gold>%example_clicks% clicks",
                        List.of("<gray>Click NPCs to count up."), List.of()),
                MenuButton.withActions(22, Material.BARRIER, "<red>Start again", List.of(),
                        List.of("example_reset", "close_menu")))));

        if (config.contains(ZONE + ".from") && config.contains(ZONE + ".to")) {
            context.regions().define(ZONE, point(config.getString(ZONE + ".from", "0 0 0")),
                    point(config.getString(ZONE + ".to", "0 0 0")));
            String greeting = config.getString(ZONE + ".message", "<prefix>Welcome!");
            context.events().listen(RegionEnterEvent.class, event -> {
                if (event.owner().equals("addon:" + context.description().id()) && event.region().equals(ZONE)) {
                    context.texts().send(event.getPlayer(), greeting);
                }
            });
        }
        context.logger().info("Counting NPC clicks (a message every {} clicks)", milestone);
    }

    @Override
    public void onDisable(AddonContext context) {
        // Listeners, the command, the menu, the region and the service are removed by the lobby.
        clicks.clear();
    }

    @Override
    public int clicks(UUID player) {
        return clicks.getOrDefault(player, 0);
    }

    /** Sets a player's count in memory and saves it in the background. Returns the new count. */
    private int setClicks(Player player, int value) {
        clicks.put(player.getUuid(), value);
        context.playerSettings().set(player.getUuid(), CLICKS_KEY, String.valueOf(value));
        return value;
    }

    /** Loads a player's saved count off the tick thread (playerSettings never blocks). */
    private void load(Player player) {
        context.playerSettings().get(player.getUuid(), CLICKS_KEY).thenAccept(saved -> {
            if (player.isOnline()) {
                clicks.putIfAbsent(player.getUuid(), saved.map(ClickCounterAddon::number).orElse(0));
            }
        });
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "x y z" from the config. */
    private static Vec point(String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("A point needs x y z, not: " + text);
        }
        return new Vec(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
    }
}
