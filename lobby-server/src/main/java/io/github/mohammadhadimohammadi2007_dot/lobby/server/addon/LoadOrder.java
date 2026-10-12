package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonDescription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The order addons are loaded and enabled in: every addon after the ones it depends on (and the soft
 * dependencies that are present), otherwise by id, so the order is the same on every start.
 *
 * @param order    the addons that can be loaded, in order
 * @param problems the others: why each one cannot be loaded
 */
public record LoadOrder(List<AddonDescription> order, Map<String, Problem> problems) {

    /** Why an addon cannot be loaded. */
    public record Problem(AddonState state, String detail) {
    }

    /** Sorts the addons. Pure; never throws. */
    public static LoadOrder of(Collection<AddonDescription> addons) {
        Map<String, AddonDescription> remaining = new TreeMap<>();
        for (AddonDescription addon : addons) {
            remaining.put(addon.id(), addon);
        }
        Map<String, Problem> problems = new LinkedHashMap<>();
        // Missing dependencies, repeated because removing one addon can leave another without its dependency.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (AddonDescription addon : List.copyOf(remaining.values())) {
                for (String dependency : addon.depends()) {
                    if (!remaining.containsKey(dependency)) {
                        String why = problems.containsKey(dependency)
                                ? "needs '" + dependency + "', which cannot be loaded"
                                : "needs '" + dependency + "', which is not in the addons folder";
                        problems.put(addon.id(), new Problem(AddonState.MISSING_DEPENDENCY, why));
                        remaining.remove(addon.id());
                        changed = true;
                        break;
                    }
                }
            }
        }
        // Kahn's algorithm; the smallest id goes first among those that are ready.
        Map<String, Integer> waitingFor = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (AddonDescription addon : remaining.values()) {
            int count = 0;
            for (String dependency : dependencies(addon)) {
                // An addon depending on itself waits for itself forever, so it ends up as a cycle below.
                if (remaining.containsKey(dependency)) {
                    dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(addon.id());
                    count++;
                }
            }
            waitingFor.put(addon.id(), count);
        }
        TreeSet<String> ready = new TreeSet<>();
        waitingFor.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });
        List<AddonDescription> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.pollFirst();
            order.add(remaining.get(id));
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                if (waitingFor.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (order.size() < remaining.size()) {
            TreeSet<String> stuck = new TreeSet<>(remaining.keySet());
            order.forEach(addon -> stuck.remove(addon.id()));
            for (String id : stuck) {
                problems.put(id, new Problem(AddonState.DEPENDENCY_CYCLE, "its dependencies go round in a circle ("
                        + String.join(", ", stuck) + "), so none of them can start first"));
            }
        }
        return new LoadOrder(List.copyOf(order), Map.copyOf(problems));
    }

    /** Hard and soft dependencies, without duplicates. */
    private static List<String> dependencies(AddonDescription addon) {
        List<String> all = new ArrayList<>(addon.depends());
        for (String soft : addon.softDepends()) {
            if (!all.contains(soft)) {
                all.add(soft);
            }
        }
        return all;
    }
}
