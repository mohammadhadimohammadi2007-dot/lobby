package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonDescription;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadOrderTest {

    private static AddonDescription addon(String id, List<String> depends, List<String> softDepends) {
        return new AddonDescription(id, id, "1", "x.Main", "0.1", List.of(), depends, softDepends);
    }

    private static List<String> ids(LoadOrder order) {
        return order.order().stream().map(AddonDescription::id).toList();
    }

    @Test
    void withoutDependenciesTheOrderIsById() {
        LoadOrder order = LoadOrder.of(List.of(addon("c", List.of(), List.of()), addon("a", List.of(), List.of()),
                addon("b", List.of(), List.of())));
        assertEquals(List.of("a", "b", "c"), ids(order));
        assertTrue(order.problems().isEmpty());
    }

    @Test
    void dependenciesAndPresentSoftDependenciesComeFirst() {
        LoadOrder order = LoadOrder.of(List.of(
                addon("a", List.of("c"), List.of()),
                addon("b", List.of(), List.of("a", "not-installed")),
                addon("c", List.of(), List.of())));
        assertEquals(List.of("c", "a", "b"), ids(order));
        assertTrue(order.problems().isEmpty(), "a missing soft dependency is fine");
    }

    @Test
    void aMissingDependencyAlsoStopsWhatDependsOnIt() {
        LoadOrder order = LoadOrder.of(List.of(
                addon("a", List.of("missing"), List.of()),
                addon("b", List.of("a"), List.of()),
                addon("c", List.of(), List.of("a"))));
        assertEquals(List.of("c"), ids(order));
        assertEquals(AddonState.MISSING_DEPENDENCY, order.problems().get("a").state());
        assertTrue(order.problems().get("a").detail().contains("not in the addons folder"));
        assertEquals(AddonState.MISSING_DEPENDENCY, order.problems().get("b").state());
        assertTrue(order.problems().get("b").detail().contains("cannot be loaded"));
    }

    @Test
    void cyclesAreReportedWithEveryMember() {
        LoadOrder order = LoadOrder.of(List.of(
                addon("a", List.of("b"), List.of()),
                addon("b", List.of(), List.of("a")),
                addon("self", List.of("self"), List.of()),
                addon("fine", List.of(), List.of())));
        assertEquals(List.of("fine"), ids(order));
        assertEquals(AddonState.DEPENDENCY_CYCLE, order.problems().get("a").state());
        assertEquals(AddonState.DEPENDENCY_CYCLE, order.problems().get("b").state());
        assertEquals(AddonState.DEPENDENCY_CYCLE, order.problems().get("self").state());
    }
}
