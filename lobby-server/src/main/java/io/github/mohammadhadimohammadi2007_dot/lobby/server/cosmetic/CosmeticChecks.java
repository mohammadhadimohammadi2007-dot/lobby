package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.Check;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The cosmetics lines of {@code /setup-lobby setup}. Pure. */
public final class CosmeticChecks {

    private static final String TITLE = "Cosmetics";
    private static final String OPEN_MENU = "open_menu: " + CosmeticsMenu.NAME;

    private CosmeticChecks() {
    }

    /** @param ranksPlugin true if a permissions plugin (LuckPerms) can give players permissions */
    public static List<Check> setup(CosmeticsConfig config, HotbarConfig hotbar, boolean ranksPlugin) {
        List<Check> checks = new ArrayList<>();
        if (!config.enabled()) {
            checks.add(Check.info(TITLE, "off in cosmetics.yml (enabled: false)", null));
            return checks;
        }
        if (config.count() == 0) {
            checks.add(Check.info(TITLE, "cosmetics.yml has none", null));
            return checks;
        }
        long free = config.cosmetics().values().stream().flatMap(List::stream)
                .filter(cosmetic -> cosmetic.permission().isEmpty()).count();
        StringBuilder counts = new StringBuilder();
        for (CosmeticType type : CosmeticType.values()) {
            counts.append(counts.isEmpty() ? "" : ", ").append(config.of(type).size()).append(' ').append(type.section());
        }
        checks.add(Check.ok(TITLE, String.format(Locale.ROOT, "%s (%d free for everyone)", counts, free)));
        boolean menuItem = hotbar.enabled() && hotbar.items().stream().anyMatch(item -> item.actions().actions().stream()
                .anyMatch(action -> action.describe().equalsIgnoreCase(OPEN_MENU)));
        if (!menuItem) {
            checks.add(Check.info("Cosmetics menu", "no hotbar item opens it, so players only find /cosmetics. Add an"
                    + " item with \"" + OPEN_MENU + "\" to hotbar.yml (docs/cosmetics.md)", null));
        }
        boolean gadgetSlot = hotbar.enabled() && hotbar.items().stream().anyMatch(HotbarConfig.Item::gadget);
        if (!config.of(CosmeticType.GADGET).isEmpty() && !gadgetSlot) {
            checks.add(Check.warn("Gadgets", "players can pick a gadget but cannot use it: hotbar.yml has no item"
                    + " with \"type: gadget\" (docs/cosmetics.md)", null));
        }
        if ((!config.of(CosmeticType.HAT).isEmpty() || !config.of(CosmeticType.OUTFIT).isEmpty()) && !hotbar.enabled()) {
            checks.add(Check.warn("Hats and outfits", "they are put on with the hotbar items, and hotbar.yml has"
                    + " enabled: false", null));
        }
        if (!ranksPlugin && free < config.count()) {
            checks.add(Check.info("Locked cosmetics", (config.count() - free) + " need a permission, and without"
                    + " LuckPerms only operators have it. Give them with LuckPerms, or set permission: \"\"", null));
        }
        return checks;
    }
}
