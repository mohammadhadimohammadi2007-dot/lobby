package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataNodes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * One operator in {@code data/ops.yml}.
 *
 * @param uuid      the account, or {@code null} while only the name is known (opped while offline behind a
 *                  proxy, or moved from the old {@code operators:} list); bound on their first join
 * @param name      the last known name
 * @param confirmed false until the operator confirmed with a code from the console, where that is needed
 * @param ips       the addresses they confirmed from (only used by {@code protection: code-and-ip})
 */
public record Op(@Nullable UUID uuid, String name, boolean confirmed, Set<String> ips) {

    /** Most addresses remembered per operator; the oldest is forgotten first. */
    static final int MAX_IPS = 10;

    public Op {
        ips = Set.copyOf(ips);
    }

    /** The key in ops.yml: the UUID, or {@code pending-<name>} while the UUID is unknown. */
    public String key() {
        return uuid != null ? uuid.toString() : "pending-" + name.toLowerCase(Locale.ROOT);
    }

    Op withUuid(UUID value) {
        return new Op(value, name, confirmed, ips);
    }

    Op withName(String value) {
        return new Op(uuid, value, confirmed, ips);
    }

    /** Confirmed from {@code ip}, which is remembered (keeping at most {@link #MAX_IPS}). */
    Op confirmedFrom(@Nullable String ip) {
        Set<String> updated = new LinkedHashSet<>(ips);
        if (ip != null) {
            updated.remove(ip);
            updated.add(ip);
            while (updated.size() > MAX_IPS) {
                updated.remove(updated.iterator().next());
            }
        }
        return new Op(uuid, name, true, updated);
    }

    /**
     * ops.yml:
     * <pre>
     * 069a79f4-44e9-4726-a5be-fca90e38aaf5:
     *   name: Notch
     *   confirmed: true
     *   ips: ["203.0.113.7"]
     * pending-jeb_:
     *   name: jeb_
     * </pre>
     */
    static final class Codec implements DataCodec<Op> {

        @Override
        public Op read(ConfigurationNode node) throws DataException {
            String key = String.valueOf(node.key());
            String name = node.node("name").getString("").strip();
            if (name.isEmpty()) {
                throw new DataException("it has no name");
            }
            UUID uuid = null;
            if (!key.startsWith("pending-")) {
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    throw new DataException("'" + key + "' is not a UUID (use pending-<name> for an operator who"
                            + " has not joined yet)");
                }
            }
            List<String> ips = DataNodes.stringList(node.node("ips"));
            return new Op(uuid, name, node.node("confirmed").getBoolean(false), new LinkedHashSet<>(ips));
        }

        @Override
        public void write(Op value, ConfigurationNode node) throws SerializationException {
            node.node("name").set(value.name());
            node.node("confirmed").set(value.confirmed());
            if (!value.ips().isEmpty()) {
                node.node("ips").setList(String.class, List.copyOf(value.ips()));
            }
        }
    }
}
