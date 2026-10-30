package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Cosmetics;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic.Cosmetic;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic.CosmeticType;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic.CosmeticsService;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The cosmetics for addons. */
final class ApiCosmetics implements Cosmetics {

    private final CosmeticsService cosmetics;

    ApiCosmetics(CosmeticsService cosmetics) {
        this.cosmetics = cosmetics;
    }

    private static CosmeticType type(Kind kind) {
        return CosmeticType.valueOf(kind.name());
    }

    @Override
    public boolean enabled() {
        return cosmetics.config().enabled();
    }

    @Override
    public List<String> ids(Kind kind) {
        return cosmetics.config().of(type(kind)).stream().map(Cosmetic::id).toList();
    }

    @Override
    public @Nullable String equipped(Player player, Kind kind) {
        Cosmetic cosmetic = cosmetics.equipped(player, type(kind));
        return cosmetic == null ? null : cosmetic.id();
    }

    @Override
    public boolean unlocked(Player player, Kind kind, String id) {
        Cosmetic cosmetic = cosmetics.config().find(type(kind), id);
        return cosmetic != null && cosmetics.unlocked(player, cosmetic);
    }

    @Override
    public boolean equip(Player player, Kind kind, String id) {
        Cosmetic cosmetic = cosmetics.config().find(type(kind), id);
        return cosmetic != null && cosmetics.equip(player, cosmetic) == CosmeticsService.Change.DONE;
    }

    @Override
    public void unequip(Player player, Kind kind) {
        cosmetics.unequip(player, type(kind));
    }

    @Override
    public boolean hidesOthers(Player player) {
        return cosmetics.choices(player).hideOthers();
    }
}
