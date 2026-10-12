package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.hologram.Holograms;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramType;
import net.minestom.server.coordinate.Pos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Holograms for addons, through the same service as {@code /hologram}. */
final class ApiHolograms implements Holograms {

    private final HologramService holograms;

    ApiHolograms(HologramService holograms) {
        this.holograms = holograms;
    }

    @Override
    public Set<String> names() {
        Set<String> names = new TreeSet<>();
        holograms.all().forEach(data -> names.add(data.name()));
        return names;
    }

    @Override
    public boolean exists(String name) {
        return holograms.get(key(name)) != null;
    }

    @Override
    public boolean create(String name, Pos position, List<String> lines) {
        String key = key(name);
        return HologramService.validName(key) && holograms.create(key, HologramType.TEXT, position, List.copyOf(lines)) != null;
    }

    @Override
    public boolean setLines(String name, List<String> lines) {
        return change(name, data -> data.lines(List.copyOf(lines)));
    }

    @Override
    public boolean move(String name, Pos position) {
        return change(name, data -> data.position(position));
    }

    @Override
    public List<String> setActions(String name, List<String> actions) {
        List<String> problems = new ArrayList<>();
        synchronized (holograms) {
            HologramData data = holograms.get(key(name));
            if (data == null) {
                throw new IllegalArgumentException("There is no hologram named '" + name + "'");
            }
            ActionParser.parseList(actions, 0, name, problems::add);
            holograms.setActions(data, List.copyOf(actions), data.actions().cooldownMillis());
        }
        return problems;
    }

    @Override
    public boolean delete(String name) {
        return holograms.delete(key(name));
    }

    private boolean change(String name, Consumer<HologramData> change) {
        synchronized (holograms) {
            HologramData data = holograms.get(key(name));
            if (data == null) {
                return false;
            }
            change.accept(data);
            holograms.changed(data);
            return true;
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
