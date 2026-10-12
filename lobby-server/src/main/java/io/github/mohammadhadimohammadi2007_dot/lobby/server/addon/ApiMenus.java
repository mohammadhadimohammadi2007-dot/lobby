package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.MenuButton;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.MenuView;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.Menus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuDefinition;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuItem;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/** An addon's menus, built in code, and the lobby's menus to open. */
final class ApiMenus implements Menus {

    /** Same as an item of menus.yml without its own click-cooldown. */
    private static final long CLICK_COOLDOWN_MILLIS = 250;

    private final AddonContextImpl context;
    private final AddonServices services;
    private final List<String> registered = new CopyOnWriteArrayList<>();

    ApiMenus(AddonContextImpl context, AddonServices services) {
        this.context = context;
        this.services = services;
    }

    @Override
    public void open(Player player, String menu) {
        services.menus().open(player, menu);
    }

    @Override
    public void close(Player player) {
        services.menus().close(player);
    }

    @Override
    public boolean exists(String menu) {
        return services.menus().exists(menu);
    }

    @Override
    public void register(String name, Function<Player, MenuView> builder) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Menu names are lowercase letters, digits, - and _: " + name);
        }
        if (!services.menus().addBuiltInIfAbsent(key, player -> build(key, builder, player))) {
            throw new IllegalArgumentException("A menu named '" + key + "' already exists");
        }
        registered.add(key);
    }

    void cleanup() {
        registered.forEach(services.menus()::removeBuiltIn);
        registered.clear();
    }

    private @Nullable MenuDefinition build(String name, Function<Player, MenuView> builder, Player player) {
        if (!context.running()) {
            return null;
        }
        MenuView view;
        try {
            view = builder.apply(player);
        } catch (RuntimeException | LinkageError e) {
            context.fail("building the menu '" + name + "'", e);
            return null;
        }
        if (view == null) {
            return null;
        }
        int size = view.rows() * MenuDefinition.SLOTS_PER_ROW;
        List<MenuItem> items = new ArrayList<>();
        for (MenuButton button : view.buttons()) {
            List<Integer> slots = button.slots().stream().filter(slot -> slot >= 0 && slot < size).toList();
            if (slots.size() < button.slots().size()) {
                context.logger().warn("Menu '{}': slots of '{}' outside the {} rows are left out", name, button.name(),
                        view.rows());
            }
            ActionList actions = ActionParser.parseList(button.actions(), CLICK_COOLDOWN_MILLIS,
                    "addon menu '" + name + "'", warning -> context.logger().warn("{}", warning));
            if (button.onClick() != null) {
                List<Action> withCallback = new ArrayList<>(actions.actions());
                withCallback.add(new Callback(button.onClick()));
                actions = actions.withActions(withCallback);
            }
            items.add(new MenuItem(slots, button.material(), 1, button.name(), button.lore(), button.glow(), List.of(),
                    actions));
        }
        return new MenuDefinition(name, view.title(), view.rows(), 0, null, items);
    }

    /** A button's {@code onClick}, as the last action of its list. */
    private final class Callback implements Action {

        private final Consumer<Player> onClick;

        Callback(Consumer<Player> onClick) {
            this.onClick = onClick;
        }

        @Override
        public Step run(ActionContext run) {
            if (!context.running()) {
                return Step.STOP;
            }
            try {
                onClick.accept(run.player());
            } catch (RuntimeException | LinkageError e) {
                context.fail("a click in " + run.source(), e);
                return Step.STOP;
            }
            return Step.CONTINUE;
        }

        @Override
        public String describe() {
            return "addon click handler";
        }
    }
}
