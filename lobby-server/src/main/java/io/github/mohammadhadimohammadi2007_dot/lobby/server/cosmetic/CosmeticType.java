package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** The kinds of cosmetics. A player uses at most one of each kind at a time. */
public enum CosmeticType {
    /** Particles that follow the player. */
    TRAIL("trail", "trails"),
    /** An item or head worn on the head. */
    HAT("hat", "hats"),
    /** An item in the hotbar that does something fun when right-clicked, with a cooldown. */
    GADGET("gadget", "gadgets");

    private final String configName;
    private final String section;

    CosmeticType(String configName, String section) {
        this.configName = configName;
        this.section = section;
    }

    /** The name in permissions, saved choices and placeholders, e.g. {@code trail}. */
    public String configName() {
        return configName;
    }

    /** The section of cosmetics.yml, e.g. {@code trails}. */
    public String section() {
        return section;
    }

    /** The type with this name ({@code trail} or {@code trails}, any case), or {@code null}. */
    public static @Nullable CosmeticType fromName(String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        for (CosmeticType type : values()) {
            if (type.configName.equals(key) || type.section.equals(key)) {
                return type;
            }
        }
        return null;
    }
}
