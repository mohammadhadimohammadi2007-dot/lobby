package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import net.hollowcube.polar.PolarWorldAccess;
import net.minestom.server.MinecraftServer;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Biome lookups for Polar that work with the current Minestom version.
 *
 * <p>Compatibility fix: Polar 1.16.0 was built against Minestom 26.1.2 and its default biome lookup calls
 * {@code RegistryKey.unsafeOf}, which Minestom removed in 2026.10.05 (it became {@code RegistryKey.of}).
 * Overriding both biome methods here means Polar never calls the removed method.
 * This class can be deleted once a Polar release targets the new Minestom API.
 */
final class LobbyPolarWorldAccess implements PolarWorldAccess {

    static final LobbyPolarWorldAccess INSTANCE = new LobbyPolarWorldAccess();

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyPolarWorldAccess.class);
    private static final int NOT_FOUND = -1;

    private LobbyPolarWorldAccess() {
    }

    @Override
    public int getBiomeId(@NotNull String name) {
        try {
            int id = MinecraftServer.getBiomeRegistry().getId(RegistryKey.of(name));
            if (id != NOT_FOUND) {
                return id;
            }
        } catch (IllegalArgumentException invalidName) {
            // Falls through to plains below.
        }
        LOGGER.warn("Unknown biome '{}' in the map, using plains instead", name);
        return MinecraftServer.getBiomeRegistry().getId(Biome.PLAINS);
    }

    @Override
    public @NotNull String getBiomeName(int id) {
        RegistryKey<Biome> key = MinecraftServer.getBiomeRegistry().getKey(id);
        return key != null ? key.name() : Biome.PLAINS.name();
    }
}
