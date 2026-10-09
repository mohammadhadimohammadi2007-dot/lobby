package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a player NPC's skin comes from, as written after {@code /npc skin <npc>} or under {@code skin:}
 * in the data file. The same words as FancyNpcs where it has them.
 *
 * <ul>
 *   <li>{@code @none}: Minecraft's default skin</li>
 *   <li>{@code @mirror}: every player sees the NPC with their own skin</li>
 *   <li>{@code Notch}: the skin of that premium account, from Mojang</li>
 *   <li>{@code sr:<name>}: a custom skin saved in SkinsRestorer</li>
 *   <li>{@code mineskin:<uuid>} or a {@code https://mineskin.org/...} link: a skin already uploaded to
 *       MineSkin (reading one needs no API key)</li>
 *   <li>{@code @texture}: a texture value and signature written in the data file</li>
 * </ul>
 *
 * @param kind   where to look
 * @param source the name or id to look up; empty for the kinds that need none
 */
public record NpcSkin(Kind kind, String source) {

    /** Where a skin is looked up. */
    public enum Kind {
        DEFAULT,
        MIRROR,
        PLAYER,
        SKINSRESTORER,
        MINESKIN,
        TEXTURE
    }

    public static final NpcSkin DEFAULT = new NpcSkin(Kind.DEFAULT, "");
    public static final NpcSkin MIRROR = new NpcSkin(Kind.MIRROR, "");
    public static final NpcSkin TEXTURE = new NpcSkin(Kind.TEXTURE, "");

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern MINESKIN_UUID = Pattern.compile("[0-9a-fA-F]{32}|[0-9a-fA-F-]{36}");
    /** A link to a skin's page on MineSkin, which ends with the skin's uuid. */
    private static final Pattern MINESKIN_LINK = Pattern.compile(
            "https?://(?:www\\.)?mineskin\\.org/(?:skins/)?([0-9a-fA-F-]{32,36})/?");
    private static final String SKINSRESTORER_PREFIX = "sr:";
    private static final String MINESKIN_PREFIX = "mineskin:";

    /** Thrown for a skin that cannot be used; the message says why, in words for an admin. */
    public static final class SkinException extends Exception {
        public SkinException(String message) {
            super(message);
        }
    }

    /**
     * Reads what an admin typed.
     *
     * @throws SkinException if it is none of the accepted forms, for example a link to an image, which
     *                       would need MineSkin to upload it first and that needs an API key
     */
    public static NpcSkin parse(String text) throws SkinException {
        String value = text.strip();
        String lower = value.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "@none", "none", "default" -> {
                return DEFAULT;
            }
            case "@mirror", "mirror" -> {
                return MIRROR;
            }
            case "@texture", "texture" -> {
                return TEXTURE;
            }
            default -> {
                // Read below.
            }
        }
        if (lower.startsWith(SKINSRESTORER_PREFIX)) {
            String name = value.substring(SKINSRESTORER_PREFIX.length()).strip();
            if (name.isEmpty()) {
                throw new SkinException("sr: needs the name of a SkinsRestorer skin, for example sr:knight");
            }
            return new NpcSkin(Kind.SKINSRESTORER, name);
        }
        String mineskin = mineskinId(value);
        if (mineskin != null) {
            return new NpcSkin(Kind.MINESKIN, mineskin);
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            throw new SkinException("a link to an image needs uploading to MineSkin first, which needs a"
                    + " MineSkin API key this lobby does not have. Upload it on mineskin.org and use that"
                    + " skin's link instead");
        }
        if (PLAYER_NAME.matcher(value).matches()) {
            return new NpcSkin(Kind.PLAYER, value);
        }
        throw new SkinException("'" + value + "' is not a player name, @mirror, @none, sr:<name> or a"
                + " MineSkin link");
    }

    /** The MineSkin uuid in {@code mineskin:<uuid>} or a MineSkin link, without dashes, or {@code null}. */
    private static @Nullable String mineskinId(String value) {
        String candidate = null;
        if (value.toLowerCase(Locale.ROOT).startsWith(MINESKIN_PREFIX)) {
            candidate = value.substring(MINESKIN_PREFIX.length()).strip();
        } else {
            Matcher link = MINESKIN_LINK.matcher(value);
            if (link.matches()) {
                candidate = link.group(1);
            }
        }
        if (candidate == null || !MINESKIN_UUID.matcher(candidate).matches()) {
            return null;
        }
        return candidate.replace("-", "").toLowerCase(Locale.ROOT);
    }

    /** What an admin would type to get this skin again, for the data file and {@code /npc info}. */
    public String describe() {
        return switch (kind) {
            case DEFAULT -> "@none";
            case MIRROR -> "@mirror";
            case TEXTURE -> "@texture";
            case PLAYER -> source;
            case SKINSRESTORER -> SKINSRESTORER_PREFIX + source;
            case MINESKIN -> MINESKIN_PREFIX + source;
        };
    }

    /** True if the skin is looked up somewhere, so it can be fetched again. */
    public boolean fetched() {
        return kind == Kind.PLAYER || kind == Kind.SKINSRESTORER || kind == Kind.MINESKIN;
    }
}
