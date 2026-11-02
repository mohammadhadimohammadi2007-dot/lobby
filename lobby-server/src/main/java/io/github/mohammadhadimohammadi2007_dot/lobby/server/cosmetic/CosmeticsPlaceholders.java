package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * {@code %cosmetics_...%}: {@code trail}, {@code hat}, {@code gadget}, {@code pet}, {@code balloon} and
 * {@code outfit} (the name of what the player uses, without colors, or {@code none}); {@code unlocked} (how many they
 * have unlocked) and {@code total} (how many there are, of those their client can show).
 */
public final class CosmeticsPlaceholders implements PlaceholderNamespace {

    private static final Duration SHORT = Duration.ofSeconds(1);
    private static final String NONE = "none";

    private final CosmeticsService cosmetics;

    public CosmeticsPlaceholders(CosmeticsService cosmetics) {
        this.cosmetics = cosmetics;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        CosmeticType type = CosmeticType.fromName(params);
        if (type != null && type.configName().equals(params)) {
            return Placeholder.player(SHORT, player -> {
                Cosmetic using = cosmetics.equipped(player, type);
                return using == null ? NONE : MiniMessage.miniMessage().stripTags(using.name());
            });
        }
        return switch (params) {
            case "unlocked" -> Placeholder.player(SHORT, player -> {
                int count = 0;
                for (CosmeticType each : CosmeticType.values()) {
                    count += cosmetics.unlockedCount(player, each);
                }
                return String.valueOf(count);
            });
            case "total" -> Placeholder.player(SHORT, player -> {
                int count = 0;
                for (CosmeticType each : CosmeticType.values()) {
                    count += (int) cosmetics.config().of(each).stream()
                            .filter(cosmetic -> cosmetics.supported(player, cosmetic)).count();
                }
                return String.valueOf(count);
            });
            default -> null;
        };
    }
}
