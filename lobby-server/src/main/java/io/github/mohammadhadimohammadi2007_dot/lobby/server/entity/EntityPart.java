package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One entity as the client should see it: everything needed to spawn it and nothing else. It only exists
 * in packets, never in the world, so it costs no server tick time and cannot be hit or pushed.
 *
 * <p>Two parts that are equal produce the very same packets, which is how the renderer groups viewers who
 * can share one entity.
 *
 * @param metadata   entity metadata by index, from {@link Metadata}
 * @param equipment  held and worn items, empty for most parts
 * @param profile    for a {@code PLAYER} part: the name and skin the client must know before it can spawn
 *                   it; {@code null} for every other part
 * @param uuid       the UUID the client knows the entity by, or {@code null} to let the renderer pick one.
 *                   Set it when something else refers to the entity by UUID, such as a team for its glow
 *                   colour
 * @param scale      the {@code scale} attribute (1.20.5+ clients); 1 sends nothing. Only used for entity
 *                   types that have attributes, as a client disconnects on attributes for any other
 * @param passengers indices of other parts of the same object that ride this one, for example an NPC
 *                   sitting on an invisible seat
 */
public record EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata,
                         Map<EquipmentSlot, ItemStack> equipment, @Nullable Profile profile, @Nullable UUID uuid,
                         double scale, List<Integer> passengers) {

    /**
     * The player profile of a player-shaped part. A client only draws a player it has in its player
     * list, with the skin from that list.
     *
     * @param name    at most 16 characters; hidden above the head by the lobby's hidden-name team
     * @param skin    the skin, or {@code null} for Minecraft's default one
     * @param listing whether the entry shows in the tab list
     * @param tabName the name shown in the tab list for {@link Listing#LISTED}, or {@code null}
     */
    public record Profile(String name, @Nullable PlayerSkin skin, Listing listing, @Nullable Component tabName) {

        public Profile(String name, @Nullable PlayerSkin skin) {
            this(name, skin, Listing.HIDDEN, null);
        }

        /** True if a client needs a fresh entry (and so a fresh spawn) to go from one to the other. */
        boolean sameEntry(Profile other) {
            return name.equals(other.name) && Objects.equals(skin, other.skin) && listing == other.listing;
        }
    }

    /** How a profile appears in the viewer's tab list. */
    public enum Listing {
        /** Never shown: what 1.19.3+ clients get. */
        HIDDEN,
        /**
         * Shown, then removed after a short delay. Older clients (1.8 through ViaRewind) have no hidden
         * entries and often keep the default skin if the entry is not listed while the player spawns.
         */
        TEMPORARY,
        /** Shown for as long as the entity is (an NPC with {@code show_in_tab}). */
        LISTED
    }

    public EntityPart {
        metadata = Map.copyOf(metadata);
        equipment = Map.copyOf(equipment);
        passengers = List.copyOf(passengers);
    }

    public EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata) {
        this(type, position, metadata, Map.of(), null);
    }

    public EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata,
                      Map<EquipmentSlot, ItemStack> equipment) {
        this(type, position, metadata, equipment, null);
    }

    public EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata,
                      Map<EquipmentSlot, ItemStack> equipment, @Nullable Profile profile) {
        this(type, position, metadata, equipment, profile, null, 1, List.of());
    }

    /** The same part at another position. */
    public EntityPart at(Pos newPosition) {
        return new EntityPart(type, newPosition, metadata, equipment, profile, uuid, scale, passengers);
    }

    /** The same part moved by {@code offset}. */
    public EntityPart moved(Vec offset) {
        return at(position.add(offset));
    }

    /** The same part known to clients by this UUID. */
    public EntityPart withUuid(UUID value) {
        return new EntityPart(type, position, metadata, equipment, profile, value, scale, passengers);
    }

    /** The same part at another size; ignored for entity types without attributes. */
    public EntityPart withScale(double value) {
        return new EntityPart(type, position, metadata, equipment, profile, uuid,
                type.shouldSendAttributes() ? value : 1, passengers);
    }

    /** The same part carrying the parts at these indices. */
    public EntityPart withPassengers(List<Integer> value) {
        return new EntityPart(type, position, metadata, equipment, profile, uuid, scale, value);
    }
}
