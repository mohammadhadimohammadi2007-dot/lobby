package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * The properties {@code /hologram set <name> <property> <value>} can change, each with the words it
 * accepts. Keeping them in one table means the command stays small and the help always matches what
 * really works.
 */
final class HologramProperties {

    /** What happened to one property: the value as it is now, or why the value was refused. */
    record Result(@Nullable String shown, @Nullable String error) {

        static Result ok(Object value) {
            return new Result(String.valueOf(value), null);
        }

        static Result error(String message) {
            return new Result(null, message);
        }

        boolean failed() {
            return error != null;
        }
    }

    private static final int RGB_LENGTH = 6;
    private static final int ARGB_LENGTH = 8;
    private static final int HEX = 16;
    private static final int OPAQUE = 0xFF000000;
    private static final String HEX_FORMAT = "#%08X";

    private static final Map<String, BiFunction<HologramData, String, Result>> SETTERS = new LinkedHashMap<>();
    private static final Map<String, String> ALLOWED = new LinkedHashMap<>();

    /**
     * The names FancyHolograms uses for the same properties, so an admin coming from it can type what
     * they know. See docs/holograms.md for the full mapping.
     */
    private static final Map<String, String> FANCY_NAMES = Map.of(
            "visibilitydistance", "view-distance",
            "updatetextinterval", "update-interval",
            "textshadow", "text-shadow",
            "seethrough", "see-through",
            "textalignment", "alignment");

    /** The line subcommands FancyHolograms puts under {@code edit}; this lobby has them on their own. */
    private static final Map<String, String> LINE_COMMANDS = Map.of(
            "addline", "addline",
            "setline", "setline",
            "removeline", "removeline",
            "insertbefore", "insertline",
            "insertafter", "insertafter");

    static {
        property("scale", "a number between " + HologramData.MIN_SCALE + " and " + HologramData.MAX_SCALE,
                (data, value) -> {
                    Double number = decimal(value);
                    if (number == null) {
                        return Result.error("not a number");
                    }
                    data.scale(number);
                    return Result.ok(data.scale());
                });
        property("billboard", String.join(", ", sorted(Billboard.configNames())), (data, value) -> {
            Billboard billboard = Billboard.fromConfigName(value);
            if (billboard == null) {
                return Result.error("unknown billboard");
            }
            data.billboard(billboard);
            return Result.ok(billboard.configName());
        });
        property("alignment", String.join(", ", sorted(TextAlignment.configNames())), (data, value) -> {
            TextAlignment alignment = TextAlignment.fromConfigName(value);
            if (alignment == null) {
                return Result.error("unknown alignment");
            }
            data.alignment(alignment);
            return Result.ok(alignment.configName());
        });
        property("background", "default, transparent, #RRGGBB or #AARRGGBB", (data, value) -> {
            String text = value.strip().toLowerCase(Locale.ROOT);
            if (text.equals("default")) {
                data.background(null);
                return Result.ok("default");
            }
            if (text.equals("transparent")) {
                data.background(0);
                return Result.ok("transparent");
            }
            Integer colour = colour(text);
            if (colour == null) {
                return Result.error("not a colour");
            }
            data.background(colour);
            return Result.ok(String.format(HEX_FORMAT, colour));
        });
        property("text-shadow", "true or false", (data, value) -> {
            Boolean flag = bool(value);
            if (flag == null) {
                return Result.error("not true or false");
            }
            data.textShadow(flag);
            return Result.ok(flag);
        });
        property("see-through", "true or false", (data, value) -> {
            Boolean flag = bool(value);
            if (flag == null) {
                return Result.error("not true or false");
            }
            data.seeThrough(flag);
            return Result.ok(flag);
        });
        property("view-distance", "blocks, between " + (int) HologramData.MIN_VIEW_DISTANCE + " and "
                + (int) HologramData.MAX_VIEW_DISTANCE, (data, value) -> {
            Double number = decimal(value);
            if (number == null) {
                return Result.error("not a number");
            }
            data.viewDistance(number);
            return Result.ok(data.viewDistance());
        });
        property("update-interval", "ticks, 0 for never, -1 to decide from the text", (data, value) -> {
            Integer number = integer(value);
            if (number == null) {
                return Result.error("not a whole number");
            }
            data.updateIntervalTicks(number);
            return Result.ok(data.updateIntervalTicks());
        });
        property("permission", "a permission node, or \"\" for everyone", (data, value) -> {
            data.permission(value.equals("\"\"") ? "" : value);
            return Result.ok(data.permission().isEmpty() ? "everyone" : data.permission());
        });
        property("line-spacing", "blocks between lines on old clients", (data, value) -> {
            Double number = decimal(value);
            if (number == null) {
                return Result.error("not a number");
            }
            data.lineSpacing(number);
            return Result.ok(data.lineSpacing());
        });
        property("item", "an item name, for an item hologram", (data, value) -> {
            Material material = Material.fromKey(key(value));
            if (material == null) {
                return Result.error("no such item");
            }
            data.item(material);
            return Result.ok(material.key().value());
        });
        property("block", "a block name, for a block hologram", (data, value) -> {
            Block block = Block.fromKey(key(value));
            if (block == null) {
                return Result.error("no such block");
            }
            data.block(block);
            return Result.ok(block.key().value());
        });
        property("type", String.join(", ", sorted(HologramType.configNames())), (data, value) -> {
            HologramType type = HologramType.fromConfigName(value);
            if (type == null) {
                return Result.error("unknown type");
            }
            if (type == HologramType.ITEM && data.item() == null) {
                return Result.error("set an item first: /hologram set " + data.name() + " item diamond");
            }
            if (type == HologramType.BLOCK && data.block() == null) {
                return Result.error("set a block first: /hologram set " + data.name() + " block diamond_block");
            }
            data.type(type);
            return Result.ok(type.configName());
        });
    }

    private HologramProperties() {
    }

    /** Every property name, in the order the help shows them. */
    static Set<String> names() {
        return SETTERS.keySet();
    }

    /** What a property accepts, for the error message. */
    static String allowed(String property) {
        return ALLOWED.getOrDefault(resolve(property), "");
    }

    /**
     * The command to use instead, when the "property" is really one of this lobby's own subcommands
     * (FancyHolograms edits lines through {@code edit <name> addline ...}), or {@code null}.
     */
    static @Nullable String lineCommandFor(String property) {
        return LINE_COMMANDS.get(property.toLowerCase(Locale.ROOT));
    }

    /** Changes one property, or says why it could not. {@code null} means there is no such property. */
    static @Nullable Result apply(HologramData data, String property, String value) {
        BiFunction<HologramData, String, Result> setter = SETTERS.get(resolve(property));
        return setter == null ? null : setter.apply(data, value.strip());
    }

    /** This lobby's name for a property, accepting FancyHolograms' names as well. */
    private static String resolve(String property) {
        String name = property.toLowerCase(Locale.ROOT);
        return FANCY_NAMES.getOrDefault(name, name);
    }

    private static void property(String name, String allowed, BiFunction<HologramData, String, Result> setter) {
        SETTERS.put(name, setter);
        ALLOWED.put(name, allowed);
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static @Nullable Double decimal(String value) {
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable Integer integer(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable Boolean bool(String value) {
        String text = value.strip().toLowerCase(Locale.ROOT);
        if (text.equals("true") || text.equals("yes")) {
            return Boolean.TRUE;
        }
        if (text.equals("false") || text.equals("no")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static @Nullable Integer colour(String value) {
        String digits = value.startsWith("#") ? value.substring(1) : value;
        try {
            if (digits.length() == RGB_LENGTH) {
                return OPAQUE | Integer.parseInt(digits, HEX);
            }
            if (digits.length() == ARGB_LENGTH) {
                return (int) Long.parseLong(digits, HEX);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static String key(String value) {
        String name = value.strip().toLowerCase(Locale.ROOT);
        return name.contains(":") ? name : "minecraft:" + name;
    }
}
