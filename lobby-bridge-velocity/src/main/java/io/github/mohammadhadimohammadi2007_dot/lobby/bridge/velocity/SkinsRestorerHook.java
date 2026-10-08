package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Reads a player's skin from their game profile on the proxy.
 *
 * <p>SkinsRestorer changes the profile when a player runs {@code /skin}, so the bridge watches the
 * profile instead of using SkinsRestorer's own API. That keeps the plugin free of a dependency on it
 * (SkinsRestorer is GPL) and also catches skins set by any other plugin that changes the profile.
 */
final class SkinsRestorerHook {

    /** The property Mojang and SkinsRestorer use for skins. */
    private static final String TEXTURES = "textures";

    /** A player's skin: the base64 texture value and Mojang's signature for it. */
    record Skin(String value, String signature) {
    }

    private SkinsRestorerHook() {
    }

    /** The player's current skin, or {@code null} if their profile has none. */
    static @Nullable Skin of(Player player) {
        return of(player.getGameProfileProperties());
    }

    /** The skin in these profile properties, or {@code null}. */
    static @Nullable Skin of(List<GameProfile.Property> properties) {
        for (GameProfile.Property property : properties) {
            if (TEXTURES.equals(property.getName()) && !property.getValue().isEmpty()) {
                String signature = property.getSignature();
                return new Skin(property.getValue(), signature == null ? "" : signature);
            }
        }
        return null;
    }

    /**
     * Calls {@code onChange} when a player's skin differs from what the lobby was last told.
     *
     * @param known the skin each player was last reported with
     */
    static void checkForChange(Player player, java.util.Map<UUID, Skin> known, BiConsumer<Player, Skin> onChange) {
        Skin current = of(player);
        if (current == null) {
            return;
        }
        Skin previous = known.put(player.getUniqueId(), current);
        if (!current.equals(previous)) {
            onChange.accept(player, current);
        }
    }
}
