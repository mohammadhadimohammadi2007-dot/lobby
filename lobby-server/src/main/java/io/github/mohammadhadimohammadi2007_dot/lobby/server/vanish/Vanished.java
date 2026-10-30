package io.github.mohammadhadimohammadi2007_dot.lobby.server.vanish;

import net.minestom.server.entity.Player;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is vanished right now. Read from everywhere players are counted or listed (placeholders, the server list,
 * {@code /list}, join messages), so it is one shared set instead of a service handed to all of them. Changed only
 * by {@link VanishService}. Any thread.
 */
public final class Vanished {

    private static final Set<UUID> VANISHED = ConcurrentHashMap.newKeySet();

    private Vanished() {
    }

    public static boolean is(Player player) {
        return VANISHED.contains(player.getUuid());
    }

    public static boolean is(UUID player) {
        return VANISHED.contains(player);
    }

    /** How many of {@code players} are not vanished: the count everyone may see. */
    public static int visibleCount(Collection<? extends Player> players) {
        if (VANISHED.isEmpty()) {
            return players.size();
        }
        int count = 0;
        for (Player player : players) {
            if (!VANISHED.contains(player.getUuid())) {
                count++;
            }
        }
        return count;
    }

    /** How many players are vanished. */
    public static int count() {
        return VANISHED.size();
    }

    static void set(UUID player, boolean vanished) {
        if (vanished) {
            VANISHED.add(player);
        } else {
            VANISHED.remove(player);
        }
    }

    /** Nobody vanished, for tests that start several lobbies in one JVM. */
    static void clear() {
        VANISHED.clear();
    }
}
