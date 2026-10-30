package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * What one player chose: a cosmetic of each type (or none), and whether they hide other players' cosmetics.
 * Saved with their other settings as one short text, {@code trail=flame,hat=crown,hide-others}.
 *
 * <p>The ids are kept even when the cosmetic is gone from cosmetics.yml or no longer allowed: it then simply does
 * not show, and comes back if the server owner adds it again or the player gets the permission back.
 */
public record CosmeticChoices(Map<CosmeticType, String> chosen, boolean hideOthers) {

    public static final CosmeticChoices NONE = new CosmeticChoices(Map.of(), false);
    /** Ids as in cosmetics.yml: lower case letters, digits, {@code _} and {@code -}. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final String HIDE_OTHERS = "hide-others";

    public CosmeticChoices {
        chosen = chosen.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(chosen));
    }

    /** The chosen id of this type, or {@code null}. */
    public @Nullable String get(CosmeticType type) {
        return chosen.get(type);
    }

    /** The same choices with {@code id} (or nothing, for {@code null}) as the {@code type}. */
    public CosmeticChoices with(CosmeticType type, @Nullable String id) {
        Map<CosmeticType, String> changed = new EnumMap<>(CosmeticType.class);
        changed.putAll(chosen);
        if (id == null) {
            changed.remove(type);
        } else {
            changed.put(type, id);
        }
        return new CosmeticChoices(changed, hideOthers);
    }

    public CosmeticChoices withHideOthers(boolean value) {
        return new CosmeticChoices(chosen, value);
    }

    /** The saved text, or {@code null} when nothing is chosen (so nothing needs saving). */
    public @Nullable String encode() {
        StringJoiner text = new StringJoiner(",");
        for (CosmeticType type : CosmeticType.values()) {
            String id = chosen.get(type);
            if (id != null) {
                text.add(type.configName() + "=" + id);
            }
        }
        if (hideOthers) {
            text.add(HIDE_OTHERS);
        }
        return text.length() == 0 ? null : text.toString();
    }

    /** Reads {@link #encode()}'s text; anything it does not understand is skipped. */
    public static CosmeticChoices decode(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        Map<CosmeticType, String> chosen = new EnumMap<>(CosmeticType.class);
        boolean hideOthers = false;
        for (String part : text.split(",")) {
            String entry = part.strip().toLowerCase(Locale.ROOT);
            if (entry.equals(HIDE_OTHERS)) {
                hideOthers = true;
                continue;
            }
            int equals = entry.indexOf('=');
            if (equals < 0) {
                continue;
            }
            CosmeticType type = CosmeticType.fromName(entry.substring(0, equals));
            String id = entry.substring(equals + 1);
            if (type != null && ID.matcher(id).matches()) {
                chosen.put(type, id);
            }
        }
        return new CosmeticChoices(chosen, hideOthers);
    }
}
