package io.github.mohammadhadimohammadi2007_dot.lobby.server.portal;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionEntries;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataNodes;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.Locale;

/**
 * Reads and writes one portal of {@code data/portals.yml}:
 *
 * <pre>
 * bedwars:
 *   from: [10, 64, 10]
 *   to: [12, 67, 10]
 *   cooldown: 2000
 *   permission: ""
 *   actions:
 *     - "connect_group: bedwars"
 * </pre>
 *
 * A portal without both corners is broken (kept in the file untouched and reported); anything else falls
 * back to its default.
 */
public final class PortalCodec implements DataCodec<Portal> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortalCodec.class);
    private static final long MAX_COOLDOWN_MILLIS = 60_000;

    @Override
    public Portal read(ConfigurationNode node) throws DataException {
        String name = String.valueOf(node.key()).toLowerCase(Locale.ROOT);
        int[] from = corner(node.node("from"), "from");
        int[] to = corner(node.node("to"), "to");
        Region region = new Region(Portal.OWNER, name, Math.min(from[0], to[0]), Math.min(from[1], to[1]),
                Math.min(from[2], to[2]), Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2]));
        if (region.volume() > Portal.MAX_VOLUME) {
            throw new DataException("it is " + region.volume() + " blocks big; at most " + Portal.MAX_VOLUME
                    + " are allowed. Check its corners");
        }
        long cooldown = Math.clamp(node.node("cooldown").getLong(Portal.DEFAULT_COOLDOWN_MILLIS), 0, MAX_COOLDOWN_MILLIS);
        Portal portal = new Portal(name, region, List.of(), ActionList.EMPTY,
                cooldown, node.node("permission").getString("").strip());
        return portal.withActions(DataNodes.entryList(node.node("actions")), warning -> LOGGER.warn("{}", warning));
    }

    @Override
    public void write(Portal portal, ConfigurationNode node) throws SerializationException {
        Region region = portal.region();
        node.node("from").setList(Integer.class, List.of(region.minX(), region.minY(), region.minZ()));
        node.node("to").setList(Integer.class, List.of(region.maxX(), region.maxY(), region.maxZ()));
        node.node("cooldown").set(portal.cooldownMillis());
        node.node("permission").set(portal.permission());
        ActionEntries.write(portal.entries(), node.node("actions"));
    }

    private static int[] corner(ConfigurationNode node, String option) throws DataException {
        List<? extends ConfigurationNode> values = node.childrenList();
        if (values.size() != 3) {
            throw new DataException("'" + option + "' must be a corner like [10, 64, 10]");
        }
        int[] corner = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!(values.get(i).raw() instanceof Number number)) {
                throw new DataException("'" + option + "' must hold three whole numbers");
            }
            corner[i] = number.intValue();
        }
        return corner;
    }
}
