package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder.PlaceholderResolver;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder.PlaceholderValue;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder.Placeholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An addon's placeholder namespaces, turned into the lobby's own {@link Placeholder} kinds so they are cached
 * and classified exactly like the built-in ones. An exception in the addon's code disables the addon, and
 * the placeholder shows as empty text.
 */
final class ApiPlaceholders implements Placeholders {

    private final AddonContextImpl context;
    private final PlaceholderService service;
    private final List<String> namespaces = new CopyOnWriteArrayList<>();

    ApiPlaceholders(AddonContextImpl context, PlaceholderService service) {
        this.context = context;
        this.service = service;
    }

    @Override
    public void register(String namespace, PlaceholderResolver resolver) {
        if (!context.loading()) {
            throw new IllegalStateException("Register placeholders in onLoad, before the lobby reads its files"
                    + " (namespace '" + namespace + "')");
        }
        service.registry().register(namespace, params -> {
            if (!context.running()) {
                return null;
            }
            PlaceholderValue value;
            try {
                value = resolver.resolve(params);
            } catch (RuntimeException | LinkageError e) {
                context.fail("placeholder %" + namespace + "_" + params + "%", e);
                return null;
            }
            return value == null ? null : convert(namespace + "_" + params, value);
        });
        namespaces.add(namespace);
    }

    @Override
    public String fill(String text, @Nullable Player player) {
        return service.plainText(text, player);
    }

    int count() {
        return namespaces.size();
    }

    void cleanup() {
        for (String namespace : namespaces) {
            service.registry().unregister(namespace);
        }
        if (!namespaces.isEmpty()) {
            service.invalidateAll();
        }
        namespaces.clear();
    }

    private Placeholder convert(String key, PlaceholderValue value) {
        return switch (value) {
            case PlaceholderValue.Constant constant -> new Placeholder.Constant(constant.value(), constant.formatted());
            case PlaceholderValue.Global global -> new Placeholder.Global(() -> guard(key, () -> global.value().get()),
                    global.cacheTime(), global.formatted());
            case PlaceholderValue.PerPlayer perPlayer -> new Placeholder.PerPlayer(
                    player -> guard(key, () -> perPlayer.value().apply(player)), perPlayer.cacheTime(), perPlayer.formatted());
            case PlaceholderValue.Relational relational -> new Placeholder.Relational(
                    (viewer, target) -> guard(key, () -> relational.value().apply(viewer, target)), relational.formatted());
        };
    }

    /** Runs the addon's code; an error disables the addon and gives empty text. */
    private String guard(String key, java.util.function.Supplier<String> code) {
        if (!context.running()) {
            return "";
        }
        try {
            String value = code.get();
            return value == null ? "" : value;
        } catch (RuntimeException | LinkageError e) {
            context.fail("placeholder %" + key + "%", e);
            return "";
        }
    }
}
