package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.npc.Npcs;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcSkin;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcTrigger;
import net.minestom.server.coordinate.Pos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** NPCs for addons, through the same service as {@code /npc}. */
final class ApiNpcs implements Npcs {

    private final NpcService npcs;

    ApiNpcs(NpcService npcs) {
        this.npcs = npcs;
    }

    @Override
    public Set<String> names() {
        Set<String> names = new TreeSet<>();
        npcs.all().forEach(data -> names.add(data.name()));
        return names;
    }

    @Override
    public boolean exists(String name) {
        return npcs.get(key(name)) != null;
    }

    @Override
    public boolean create(String name, Pos position) {
        String key = key(name);
        return NpcService.validName(key) && npcs.create(key, position) != null;
    }

    @Override
    public boolean setNameLines(String name, List<String> lines) {
        return change(name, data -> data.nameTag().lines(List.copyOf(lines)));
    }

    @Override
    public boolean move(String name, Pos position) {
        return change(name, data -> data.position(position));
    }

    @Override
    public CompletableFuture<Boolean> setSkin(String name, String skin) {
        NpcData data = npcs.get(key(name));
        if (data == null) {
            return CompletableFuture.completedFuture(false);
        }
        NpcSkin parsed;
        try {
            parsed = NpcSkin.parse(skin);
        } catch (NpcSkin.SkinException e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(e.getMessage()));
        }
        // Skins that need no lookup (@mirror, @none) come back without a skin and without a problem.
        return npcs.setSkin(data, parsed).thenApply(result -> result.found() || result.problem().isEmpty());
    }

    @Override
    public List<String> setActions(String name, Trigger trigger, List<String> actions) {
        List<String> problems = new ArrayList<>();
        synchronized (npcs) {
            NpcData data = npcs.get(key(name));
            if (data == null) {
                throw new IllegalArgumentException("There is no NPC named '" + name + "'");
            }
            ActionParser.parseList(actions, 0, name, problems::add);
            npcs.setActions(data, NpcTrigger.valueOf(trigger.name()), List.copyOf(actions));
        }
        return problems;
    }

    @Override
    public boolean delete(String name) {
        return npcs.delete(key(name));
    }

    private boolean change(String name, Consumer<NpcData> change) {
        synchronized (npcs) {
            NpcData data = npcs.get(key(name));
            if (data == null) {
                return false;
            }
            change.accept(data);
            npcs.changed(data);
            return true;
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
