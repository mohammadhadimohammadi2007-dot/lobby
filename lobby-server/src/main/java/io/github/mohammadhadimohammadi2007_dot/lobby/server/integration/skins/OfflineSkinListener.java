package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.network.player.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gives players a skin in standalone offline mode, where nobody else provides one.
 *
 * <p>Order: the skin chosen in SkinsRestorer (if that integration is on), otherwise the skin of the
 * premium account with the same name (if {@code fetch-skins-for-offline-players} is on).
 * Runs in the async login event, so the lookups never block the tick thread.
 */
public final class OfflineSkinListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfflineSkinListener.class);

    private final SkinsRestorerReader skinsRestorer;
    private final MojangSkinFetcher mojang;

    /**
     * @param skinsRestorer SkinsRestorer reader, or {@code null} if that integration is off
     * @param mojang        Mojang fetcher, or {@code null} if fetching is turned off
     */
    public OfflineSkinListener(SkinsRestorerReader skinsRestorer, MojangSkinFetcher mojang) {
        this.skinsRestorer = skinsRestorer;
        this.mojang = mojang;
    }

    /** Registers the listener on {@code node}. */
    public void register(EventNode<Event> node) {
        node.addListener(AsyncPlayerPreLoginEvent.class, this::applySkin);
    }

    private void applySkin(AsyncPlayerPreLoginEvent event) {
        GameProfile profile = event.getGameProfile();
        boolean hasSkin = profile.properties().stream()
                .anyMatch(property -> SkinData.TEXTURES_PROPERTY.equals(property.name()));
        if (hasSkin) {
            return;
        }
        findSkin(event).ifPresent(skin -> {
            List<GameProfile.Property> properties = new ArrayList<>(profile.properties());
            properties.add(skin.toProperty());
            event.setGameProfile(new GameProfile(profile.uuid(), profile.name(), properties));
        });
    }

    private Optional<SkinData> findSkin(AsyncPlayerPreLoginEvent event) {
        if (skinsRestorer != null) {
            try {
                Optional<SkinData> chosen = skinsRestorer.chosenSkinNow(event.getPlayerUuid());
                if (chosen.isPresent()) {
                    return chosen;
                }
            } catch (SQLException e) {
                LOGGER.warn("Could not read the SkinsRestorer skin of {}: {}", event.getUsername(), e.getMessage());
            }
        }
        return mojang != null ? mojang.fetch(event.getUsername()) : Optional.empty();
    }
}
