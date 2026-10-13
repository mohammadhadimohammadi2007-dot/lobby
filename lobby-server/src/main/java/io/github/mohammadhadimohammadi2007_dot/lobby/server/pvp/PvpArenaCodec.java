package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.coordinate.Pos;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The PvP area in data/pvp.yml:
 *
 * <pre>
 * area:
 *   from: [10, 60, 10]
 *   to: [40, 80, 40]
 *   spawn: {x: 25.5, y: 61.0, z: 25.5, yaw: 0.0, pitch: 0.0}
 *   kit:
 *     "0": "{count: 1, id: \"minecraft:iron_sword\"}"
 *     chestplate: "{count: 1, id: \"minecraft:iron_chestplate\"}"
 * </pre>
 */
public final class PvpArenaCodec implements DataCodec<PvpArena> {

    @Override
    public PvpArena read(ConfigurationNode node) throws DataException {
        int[] from = corner(node.node("from"), "from");
        int[] to = corner(node.node("to"), "to");
        Region region = new Region(PvpArena.OWNER, PvpArena.NAME, Math.min(from[0], to[0]), Math.min(from[1], to[1]),
                Math.min(from[2], to[2]), Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2]));
        if (region.volume() > PvpArena.MAX_VOLUME) {
            throw new DataException("it is " + region.volume() + " blocks big; at most " + PvpArena.MAX_VOLUME
                    + " are allowed. Check its corners");
        }
        Pos spawn = null;
        ConfigurationNode spawnNode = node.node("spawn");
        if (!spawnNode.virtual() && spawnNode.isMap()) {
            spawn = new Pos(number(spawnNode, "x"), number(spawnNode, "y"), number(spawnNode, "z"),
                    (float) number(spawnNode, "yaw"), (float) number(spawnNode, "pitch"));
        }
        Map<String, String> kit = new LinkedHashMap<>();
        node.node("kit").childrenMap().forEach((key, value) -> kit.put(String.valueOf(key), String.valueOf(value.raw())));
        try {
            return new PvpArena(region, spawn, Kit.decode(kit));
        } catch (IllegalArgumentException e) {
            throw new DataException(e.getMessage());
        }
    }

    @Override
    public void write(PvpArena arena, ConfigurationNode node) throws SerializationException {
        Region region = arena.region();
        node.node("from").setList(Integer.class, List.of(region.minX(), region.minY(), region.minZ()));
        node.node("to").setList(Integer.class, List.of(region.maxX(), region.maxY(), region.maxZ()));
        if (arena.spawn() != null) {
            ConfigurationNode spawn = node.node("spawn");
            spawn.node("x").set(arena.spawn().x());
            spawn.node("y").set(arena.spawn().y());
            spawn.node("z").set(arena.spawn().z());
            spawn.node("yaw").set(arena.spawn().yaw());
            spawn.node("pitch").set(arena.spawn().pitch());
        }
        ConfigurationNode kit = node.node("kit");
        for (Map.Entry<String, String> entry : arena.kit().encode().entrySet()) {
            kit.node(entry.getKey()).set(entry.getValue());
        }
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

    private static double number(ConfigurationNode node, String key) throws DataException {
        Object raw = node.node(key).raw();
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (key.equals("yaw") || key.equals("pitch")) {
            return 0;
        }
        throw new DataException("spawn." + key + " must be a number");
    }
}