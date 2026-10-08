package io.github.mohammadhadimohammadi2007_dot.lobby.server.region;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
class RegionTest {

    @Test
    void cornersInAnyOrderAndInclusive() {
        Region region = Region.between("portal", "a", new Vec(5, 70, -3), new Vec(1, 64, 2));
        assertEquals(new Region("portal", "a", 1, 64, -3, 5, 70, 2), region);
        assertTrue(region.contains(1, 64, -3));
        assertTrue(region.contains(5, 70, 2));
        assertTrue(!region.contains(6, 70, 2));
        assertEquals(5L * 7 * 6, region.volume());
        assertThrows(IllegalArgumentException.class, () -> new Region("x", "y", 1, 0, 0, 0, 0, 0));
    }

    @Test
    void indexFindsRegionsAcrossChunksAndNegativeCoordinates() {
        Region spanning = new Region("pad", "spanning", -20, 0, -20, 20, 10, 20);
        Region small = new Region("pad", "small", 3, 5, 3, 3, 5, 3);
        Region huge = new Region("pad", "huge", -100_000, 0, -100_000, 100_000, 0, 100_000);
        RegionIndex index = new RegionIndex(List.of(spanning, small, huge));

        assertEquals(List.of(spanning, small), index.at(3, 5, 3));
        assertEquals(List.of(spanning), index.at(-17, 1, 19));
        assertEquals(List.of(huge), index.at(90_000, 0, -90_000));
        assertEquals(List.of(), index.at(21, 5, 0));
        assertEquals(List.of(), index.at(0, 11, 0));
    }

    @Test
    void enterAndLeaveOnlyWhenCrossingBlocks(Env env) {
        RegionTracker tracker = new RegionTracker();
        tracker.setRegions("portal", List.of(new Region("portal", "door", 10, 40, 0, 11, 42, 0)));
        List<String> events = new ArrayList<>();
        tracker.listen("portal", new RegionListener() {
            @Override
            public void entered(Player player, Region region) {
                events.add("enter " + region.name());
            }

            @Override
            public void left(Player player, Region region) {
                events.add("leave " + region.name());
            }
        });
        tracker.listen("other", (player, region) -> events.add("wrong owner"));
        Player player = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"))
                .connect(env.createFlatInstance(), new Pos(0, 41, 0));

        tracker.update(player, new Pos(9.5, 41, 0.5));
        tracker.update(player, new Pos(10.2, 41, 0.5));
        tracker.update(player, new Pos(10.8, 41.4, 0.9)); // Same block: nothing.
        tracker.update(player, new Pos(11.5, 41, 0.5));   // Still inside.
        tracker.update(player, new Pos(12.5, 41, 0.5));
        tracker.update(player, new Pos(10.5, 41, 0.5));

        assertEquals(List.of("enter door", "leave door", "enter door"), events);
        assertEquals("door", tracker.regionsOf(player).getFirst().name());

        tracker.setRegions("portal", List.of());
        assertEquals(List.of(), tracker.regionsAt(new Pos(10.5, 41, 0.5)));
    }
}
