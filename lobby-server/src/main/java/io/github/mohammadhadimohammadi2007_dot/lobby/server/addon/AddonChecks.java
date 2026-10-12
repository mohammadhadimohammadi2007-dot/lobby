package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.Check;

import java.util.ArrayList;
import java.util.List;

/** Addons as lines of {@code /setup-lobby addons}, {@code setup} and {@code doctor}. Pure. */
public final class AddonChecks {

    private static final String RELOAD = "/setup-lobby addons reload ";

    private AddonChecks() {
    }

    /** One line per addon, for {@code /setup-lobby addons}. */
    public static List<Check> list(List<AddonInfo> addons) {
        List<Check> checks = new ArrayList<>();
        for (AddonInfo addon : addons) {
            String title = addon.name() + " " + addon.version();
            String authors = addon.authors().isEmpty() ? "" : ", by " + String.join(", ", addon.authors());
            checks.add(switch (addon.state()) {
                case ENABLED -> Check.ok(title, "enabled (" + addon.id() + authors + ")");
                case LOADED -> Check.info(title, "loaded, not enabled yet", null);
                case DISABLED -> Check.info(title, "disabled" + (addon.detail().isEmpty() ? "" : ": " + addon.detail()),
                        RELOAD + addon.id());
                default -> Check.fail(title, describe(addon), addon.state() == AddonState.FAILED
                        ? RELOAD + addon.id() : null);
            });
        }
        if (checks.isEmpty()) {
            checks.add(Check.info("Addons", "none; put addon jars in the addons folder and restart", null));
        }
        return checks;
    }

    /** The {@code /setup-lobby setup} line: how many run, if there are any. */
    public static List<Check> setup(List<AddonInfo> addons) {
        if (addons.isEmpty()) {
            return List.of();
        }
        long enabled = addons.stream().filter(addon -> addon.state() == AddonState.ENABLED).count();
        long broken = addons.size() - enabled;
        return List.of(broken == 0
                ? Check.ok("Addons", enabled + " enabled")
                : Check.warn("Addons", enabled + " enabled, " + broken + " not running", "/setup-lobby addons"));
    }

    /** Problems for {@code /setup-lobby doctor}: failed, incompatible and broken addons. */
    public static List<Check> doctor(List<AddonInfo> addons) {
        List<Check> problems = new ArrayList<>();
        for (AddonInfo addon : addons) {
            if (addon.state().problem()) {
                problems.add(Check.fail("Addon " + addon.id(), describe(addon) + hint(addon), null));
            }
        }
        return problems;
    }

    private static String describe(AddonInfo addon) {
        String what = switch (addon.state()) {
            case FAILED -> "failed";
            case INVALID -> "cannot be read";
            case INCOMPATIBLE -> "incompatible";
            case MISSING_DEPENDENCY -> "missing a dependency";
            case DEPENDENCY_CYCLE -> "dependency loop";
            default -> addon.state().name().toLowerCase(java.util.Locale.ROOT);
        };
        return what + " (" + addon.jar() + "): " + addon.detail();
    }

    private static String hint(AddonInfo addon) {
        return switch (addon.state()) {
            case FAILED -> ". The console log has the error; after fixing it: " + RELOAD + addon.id();
            case INCOMPATIBLE -> ". Get a version of the addon made for this lobby.";
            case MISSING_DEPENDENCY -> ". Put the missing addon in the addons folder and restart.";
            default -> "";
        };
    }
}
