package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramType;
import net.minestom.server.color.TeamColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One NPC as stored in {@code data/npcs.yml}.
 *
 * <p>Mutable for the same reason as {@link HologramData}: {@code /npc} changes one property of a live
 * NPC. Every field is {@code volatile}, because the display thread reads them while a command writes.
 *
 * <p>The name above the NPC is a full hologram ({@link #nameTag()}): several lines, placeholders with the
 * same classification, scale, background, shadow, billboard and view distance. It replaces FancyNpcs'
 * single display name and FancyHolograms' {@code linkwithnpc} in one. Its position always follows the
 * NPC, so it is never set directly.
 */
public final class NpcData {

    public static final double DEFAULT_VIEW_DISTANCE = 48;
    public static final double DEFAULT_TURN_DISTANCE = 5;
    public static final double MAX_TURN_DISTANCE = 32;
    /** The range of Minecraft's {@code scale} attribute. */
    public static final double MIN_SCALE = 0.0625;
    public static final double MAX_SCALE = 16;
    /** Space between the top of the NPC and the bottom of its name. */
    static final double NAME_GAP = 0.15;
    /** A profile name may be at most 16 characters. */
    private static final int MAX_PROFILE_NAME = 16;
    private static final String PROFILE_NAME_PREFIX = "npc";

    private final String name;
    private final String profileName;
    private final UUID profileId;
    private volatile Pos position;
    private volatile EntityType type = EntityType.PLAYER;
    private volatile NpcSkin skin = NpcSkin.DEFAULT;
    private volatile @Nullable PlayerSkin resolvedSkin;
    private volatile boolean turnToPlayer;
    private volatile double turnDistance = DEFAULT_TURN_DISTANCE;
    private volatile boolean glowing;
    private volatile TeamColor glowColor = TeamColor.WHITE;
    private volatile double scale = 1;
    private volatile NpcPose pose = NpcPose.STANDING;
    private volatile boolean showInTab;
    private volatile Map<EquipmentSlot, Material> equipment = Map.of();
    private volatile double viewDistance = DEFAULT_VIEW_DISTANCE;
    private volatile String permission = "";
    private volatile Map<NpcTrigger, Actions> actions = Map.of();
    private volatile long clickCooldownMillis = NpcCodec.DEFAULT_CLICK_COOLDOWN_MILLIS;
    private final HologramData nameTag;

    public NpcData(String name, Pos position, List<String> nameLines) {
        this.name = name;
        this.profileId = UUID.nameUUIDFromBytes(("lobby-npc:" + name).getBytes(StandardCharsets.UTF_8));
        this.profileName = profileNameOf(profileId);
        this.position = position;
        this.nameTag = new HologramData(name, HologramType.TEXT, position, nameLines);
        placeNameTag();
    }

    /**
     * The name the client knows this NPC by in its player list. At most 16 characters, stable for
     * the NPC's name, and hidden above the head by the lobby's hidden-name team, since the real name is
     * the name tag hologram.
     */
    private static String profileNameOf(UUID id) {
        String digits = Long.toHexString(id.getMostSignificantBits() & Long.MAX_VALUE);
        int room = MAX_PROFILE_NAME - PROFILE_NAME_PREFIX.length();
        return PROFILE_NAME_PREFIX + digits.substring(0, Math.min(digits.length(), room));
    }

    public String name() {
        return name;
    }

    /** The hidden player-list name of a player NPC. */
    public String profileName() {
        return profileName;
    }

    /** The UUID of a player NPC's profile; also used to remember who it was looking at. */
    public UUID profileId() {
        return profileId;
    }

    public Pos position() {
        return position;
    }

    /** Moves the NPC; its name tag moves with it. */
    public void position(Pos value) {
        position = value;
        placeNameTag();
    }

    public EntityType type() {
        return type;
    }

    /** Changes what the NPC is; the name tag moves to the new height. Only players have poses. */
    public void type(EntityType value) {
        type = value;
        if (value != EntityType.PLAYER) {
            pose = NpcPose.STANDING;
        }
        placeNameTag();
    }

    /** True for an NPC that looks like a player, with a skin. */
    public boolean isPlayer() {
        return type == EntityType.PLAYER;
    }

    public NpcSkin skin() {
        return skin;
    }

    /** Sets where the skin comes from. The skin itself is looked up afterwards. */
    public void skin(NpcSkin value) {
        skin = value;
    }

    /** The texture found for {@link #skin()}, or {@code null} until it was found. Saved in the file. */
    public @Nullable PlayerSkin resolvedSkin() {
        return resolvedSkin;
    }

    public void resolvedSkin(@Nullable PlayerSkin value) {
        resolvedSkin = value;
    }

    /** True if the NPC turns its head towards players near it. */
    public boolean turnToPlayer() {
        return turnToPlayer;
    }

    public void turnToPlayer(boolean value) {
        turnToPlayer = value;
    }

    /** How near a player has to be for the NPC to look at them, in blocks. */
    public double turnDistance() {
        return turnDistance;
    }

    public void turnDistance(double value) {
        turnDistance = Math.clamp(value, 1, MAX_TURN_DISTANCE);
    }

    public boolean glowing() {
        return glowing;
    }

    public void glowing(boolean value) {
        glowing = value;
    }

    /** The colour of the glowing outline, set through the NPC's team. */
    public TeamColor glowColor() {
        return glowColor;
    }

    public void glowColor(TeamColor value) {
        glowColor = value;
    }

    /** How big the NPC is, 1 for normal. Clients before 1.20.5 always see it at normal size. */
    public double scale() {
        return scale;
    }

    /** Resizes the NPC; the name tag moves to the new height. */
    public void scale(double value) {
        scale = Math.clamp(value, MIN_SCALE, MAX_SCALE);
        placeNameTag();
    }

    /** How a player NPC stands; always {@link NpcPose#STANDING} for other entities. */
    public NpcPose pose() {
        return pose;
    }

    /**
     * Changes the pose; the name tag moves to the new height.
     *
     * @return false (and nothing changes) for an NPC that is not a player
     */
    public boolean pose(NpcPose value) {
        if (!isPlayer() && value != NpcPose.STANDING) {
            return false;
        }
        pose = value;
        placeNameTag();
        return true;
    }

    /** True if a player NPC is listed in every viewer's tab list, like a real player. Off by default. */
    public boolean showInTab() {
        return showInTab;
    }

    public void showInTab(boolean value) {
        showInTab = value;
    }

    /**
     * The name this NPC is in its team by: the profile name for a player, the UUID for any other
     * entity, as that is how Minecraft matches team members.
     */
    public String teamEntry() {
        return isPlayer() ? profileName : profileId.toString();
    }

    /** What the NPC holds and wears. */
    public Map<EquipmentSlot, Material> equipment() {
        return equipment;
    }

    /** Puts an item in one slot, or empties it with {@code null}. */
    public void equipment(EquipmentSlot slot, @Nullable Material material) {
        Map<EquipmentSlot, Material> updated = new EnumMap<>(EquipmentSlot.class);
        updated.putAll(equipment);
        if (material == null || material == Material.AIR) {
            updated.remove(slot);
        } else {
            updated.put(slot, material);
        }
        equipment = Map.copyOf(updated);
    }

    public double viewDistance() {
        return viewDistance;
    }

    public void viewDistance(double value) {
        viewDistance = Math.clamp(value, HologramData.MIN_VIEW_DISTANCE, HologramData.MAX_VIEW_DISTANCE);
    }

    /** Permission needed to see the NPC, or {@code ""} for everyone. */
    public String permission() {
        return permission;
    }

    public void permission(String value) {
        permission = value.strip();
    }

    /** The name above the NPC: a full hologram that follows it. */
    public HologramData nameTag() {
        return nameTag;
    }

    /**
     * One list of actions as written and as parsed. They are set together, so they never disagree, and
     * the written lines are what is saved, exactly as the admin typed them.
     */
    public record Actions(List<Object> entries, ActionList parsed) {

        public static final Actions NONE = new Actions(List.of(), ActionList.EMPTY);

        public Actions {
            entries = List.copyOf(entries);
        }
    }

    /** What a click with this trigger runs; {@link Actions#NONE} if nothing. */
    public Actions actions(NpcTrigger trigger) {
        return actions.getOrDefault(trigger, Actions.NONE);
    }

    /** Every trigger that has actions. */
    public Map<NpcTrigger, Actions> allActions() {
        return actions;
    }

    /** Sets the list of one trigger; an empty one removes it. */
    public void actions(NpcTrigger trigger, Actions value) {
        Map<NpcTrigger, Actions> updated = new EnumMap<>(NpcTrigger.class);
        updated.putAll(actions);
        if (value.entries().isEmpty()) {
            updated.remove(trigger);
        } else {
            updated.put(trigger, value);
        }
        actions = Map.copyOf(updated);
    }

    /** The shortest time between two runs of the same list for one player. */
    public long clickCooldownMillis() {
        return clickCooldownMillis;
    }

    public void clickCooldownMillis(long value) {
        clickCooldownMillis = Math.max(0, value);
    }

    /** True if each viewer sees something of their own: a mirrored skin or a per-player name. */
    public boolean perViewer() {
        return skin.kind() == NpcSkin.Kind.MIRROR || nameTag.textScope().perPlayer();
    }

    /** The same NPC under another name, for {@code /npc copy}. */
    public NpcData copy(String newName) {
        NpcData copy = new NpcData(newName, position, nameTag.lines());
        copy.type(type);
        copy.scale(scale);
        copy.pose(pose);
        copy.skin = skin;
        copy.resolvedSkin = resolvedSkin;
        copy.turnToPlayer = turnToPlayer;
        copy.turnDistance = turnDistance;
        copy.glowing = glowing;
        copy.glowColor = glowColor;
        copy.scale = scale;
        copy.showInTab = showInTab;
        copy.equipment = equipment;
        copy.viewDistance = viewDistance;
        copy.permission = permission;
        copy.clickCooldownMillis = clickCooldownMillis;
        // Parsed lists hold who clicked when, so the copy parses its own instead of sharing them.
        for (Map.Entry<NpcTrigger, Actions> entry : actions.entrySet()) {
            copy.actions(entry.getKey(), new Actions(entry.getValue().entries(),
                    entry.getValue().parsed().withCooldown(clickCooldownMillis)));
        }
        copyLook(nameTag, copy.nameTag);
        return copy;
    }

    /** Copies everything about how a name tag looks, but not where it is. */
    static void copyLook(HologramData from, HologramData to) {
        to.frames(from.frames());
        to.scale(from.scale());
        to.billboard(from.billboard());
        to.alignment(from.alignment());
        to.background(from.background());
        to.textShadow(from.textShadow());
        to.seeThrough(from.seeThrough());
        to.viewDistance(from.viewDistance());
        to.updateIntervalTicks(from.updateIntervalTicks());
        to.lineSpacing(from.lineSpacing());
        to.brightness(from.brightnessBlock(), from.brightnessSky());
        to.shadowRadius(from.shadowRadius());
        to.shadowStrength(from.shadowStrength());
        to.textScope(from.textScope());
        to.reshapeMatters(from.reshapeMatters());
    }

    /**
     * How tall the NPC looks, from its position up: its type's height in its pose, times its scale for
     * clients that draw the scale.
     */
    public double height(boolean scaled) {
        double height = isPlayer() ? pose.playerHeight() : type.height();
        return scaled ? height * scale : height;
    }

    /** Puts the name just above the top of the NPC as clients that draw its scale see it. */
    private void placeNameTag() {
        Pos npc = position;
        nameTag.position(new Pos(npc.x(), npc.y() + height(true) + NAME_GAP, npc.z()));
    }
}
