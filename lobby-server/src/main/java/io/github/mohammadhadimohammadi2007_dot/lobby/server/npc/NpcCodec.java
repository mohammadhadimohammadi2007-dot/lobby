package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionEntries;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataNodes;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramCodec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes one NPC of {@code data/npcs.yml}.
 *
 * <p>The {@code name:} section is read exactly like a hologram (lines, frames, scale, background...),
 * because it is one. Only a missing position or an unknown entity type makes an entry broken; anything
 * else falls back to its default with a warning.
 */
public final class NpcCodec implements DataCodec<NpcData> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcCodec.class);
    private static final String FILE = "npcs.yml";
    private static final long MAX_CLICK_COOLDOWN_MILLIS = 60_000;
    /** Default wait between two clicks on the same NPC, so one click cannot run twice. */
    public static final long DEFAULT_CLICK_COOLDOWN_MILLIS = 500;

    @Override
    public NpcData read(ConfigurationNode node) throws DataException {
        String name = String.valueOf(node.key()).toLowerCase(Locale.ROOT);
        EntityType type = entityType(node.node("type").getString("player"));
        NpcData data = new NpcData(name, position(node.node("position")), List.of());
        data.type(type);
        HologramCodec.readAppearance(node.node("name"), data.nameTag(), FILE + ": " + name + ".name");
        readSkin(node, data, name);
        data.turnToPlayer(node.node("turn-to-player").getBoolean(false));
        data.turnDistance(node.node("turn-distance").getDouble(NpcData.DEFAULT_TURN_DISTANCE));
        data.glowing(node.node("glowing").getBoolean(false));
        readEquipment(node.node("equipment"), data, name);
        data.viewDistance(node.node("view-distance").getDouble(NpcData.DEFAULT_VIEW_DISTANCE));
        data.permission(node.node("permission").getString(""));
        long cooldown = Math.clamp(node.node("click-cooldown").getLong(DEFAULT_CLICK_COOLDOWN_MILLIS),
                0, MAX_CLICK_COOLDOWN_MILLIS);
        data.clickCooldownMillis(cooldown);
        for (NpcTrigger trigger : NpcTrigger.values()) {
            List<Object> entries = DataNodes.entryList(node.node(trigger.fileKey()));
            if (!entries.isEmpty()) {
                data.actions(trigger, new NpcData.Actions(entries,
                        ActionParser.parseList(entries, cooldown, FILE + ": " + name + "." + trigger.fileKey(),
                                warning -> LOGGER.warn("{}", warning))));
            }
        }
        return data;
    }

    @Override
    public void write(NpcData data, ConfigurationNode node) throws SerializationException {
        node.node("type").set(data.type().key().value());
        ConfigurationNode position = node.node("position");
        position.node("x").set(data.position().x());
        position.node("y").set(data.position().y());
        position.node("z").set(data.position().z());
        position.node("yaw").set(data.position().yaw());
        position.node("pitch").set(data.position().pitch());
        HologramCodec.writeAppearance(data.nameTag(), node.node("name"), true);
        if (data.isPlayer()) {
            node.node("skin").set(data.skin().describe());
            PlayerSkin texture = data.resolvedSkin();
            if (texture != null && data.skin().kind() != NpcSkin.Kind.MIRROR) {
                node.node("skin-texture", "value").set(texture.textures());
                node.node("skin-texture", "signature").set(texture.signature());
            }
        }
        node.node("turn-to-player").set(data.turnToPlayer());
        node.node("turn-distance").set(data.turnDistance());
        node.node("glowing").set(data.glowing());
        for (Map.Entry<EquipmentSlot, Material> item : data.equipment().entrySet()) {
            node.node("equipment", slotName(item.getKey())).set(item.getValue().key().value());
        }
        node.node("view-distance").set(data.viewDistance());
        node.node("permission").set(data.permission());
        if (!data.allActions().isEmpty()) {
            node.node("click-cooldown").set(data.clickCooldownMillis());
            for (NpcTrigger trigger : NpcTrigger.values()) {
                NpcData.Actions actions = data.actions(trigger);
                if (!actions.entries().isEmpty()) {
                    ActionEntries.write(actions.entries(), node.node(trigger.fileKey()));
                }
            }
        }
    }

    /** The entity type with that name, e.g. {@code villager}. */
    static EntityType entityType(String name) throws DataException {
        String key = name.strip().toLowerCase(Locale.ROOT);
        EntityType type = EntityType.fromKey(key.contains(":") ? key : "minecraft:" + key);
        if (type == null) {
            throw new DataException("'type': there is no entity type called '" + name + "'");
        }
        return type;
    }

    private static Pos position(ConfigurationNode node) throws DataException {
        if (node.virtual()) {
            throw new DataException("'position' is missing (it needs x, y and z)");
        }
        for (String axis : List.of("x", "y", "z")) {
            if (!(node.node(axis).raw() instanceof Number)) {
                throw new DataException("'position." + axis + "' must be a number");
            }
        }
        return new Pos(node.node("x").getDouble(), node.node("y").getDouble(), node.node("z").getDouble(),
                (float) node.node("yaw").getDouble(0), (float) node.node("pitch").getDouble(0));
    }

    private static void readSkin(ConfigurationNode node, NpcData data, String name) {
        String written = node.node("skin").getString("@none");
        try {
            data.skin(NpcSkin.parse(written));
        } catch (NpcSkin.SkinException e) {
            LOGGER.warn("{}: {}.skin: {}. Using the default skin.", FILE, name, e.getMessage());
        }
        ConfigurationNode texture = node.node("skin-texture");
        String value = texture.node("value").getString("");
        String signature = texture.node("signature").getString("");
        if (!value.isBlank() && !signature.isBlank()) {
            data.resolvedSkin(new PlayerSkin(value, signature));
        } else if (data.skin().kind() == NpcSkin.Kind.TEXTURE) {
            LOGGER.warn("{}: {}.skin is @texture, but skin-texture has no value and signature."
                    + " Using the default skin.", FILE, name);
        }
    }

    private static void readEquipment(ConfigurationNode node, NpcData data, String name) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            String slotName = String.valueOf(entry.getKey());
            EquipmentSlot slot = slot(slotName);
            String itemName = entry.getValue().getString("");
            Material material = Material.fromKey(itemName.contains(":") ? itemName : "minecraft:" + itemName.strip());
            if (slot == null || material == null) {
                LOGGER.warn("{}: {}.equipment.{}: '{}' is not a slot and an item name. Skipped.",
                        FILE, name, slotName, itemName);
                continue;
            }
            data.equipment(slot, material);
        }
    }

    /** The slot with that name, e.g. {@code main_hand} or {@code helmet}, or {@code null}. */
    static EquipmentSlot slot(String name) {
        String wanted = name.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.name().equals(wanted)) {
                return slot;
            }
        }
        return null;
    }

    /** How a slot is written in the file: {@code main_hand}. */
    static String slotName(EquipmentSlot slot) {
        return slot.name().toLowerCase(Locale.ROOT);
    }
}
