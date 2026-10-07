package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins;

import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.network.player.GameProfile;

/**
 * A signed Minecraft skin, as stored by Mojang and SkinsRestorer.
 *
 * @param value     base64 texture data
 * @param signature Mojang's signature of {@code value}; clients ignore unsigned skins
 */
public record SkinData(String value, String signature) {

    /** Name of the game profile property that carries the skin. */
    public static final String TEXTURES_PROPERTY = "textures";

    /** As a game profile property. */
    public GameProfile.Property toProperty() {
        return new GameProfile.Property(TEXTURES_PROPERTY, value, signature);
    }

    /** As a Minestom skin, for NPCs and {@code Player#setSkin}. */
    public PlayerSkin toPlayerSkin() {
        return new PlayerSkin(value, signature);
    }
}
