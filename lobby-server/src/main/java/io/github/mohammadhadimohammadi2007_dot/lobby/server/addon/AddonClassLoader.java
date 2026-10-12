package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;

/**
 * Loads one addon's classes from its jar.
 *
 * <p>An addon sees the Java platform, the lobby API, Minestom and the libraries Minestom's API is made of
 * (Adventure, SLF4J, Gson, fastutil, JetBrains annotations), the addons it depends on, and its own jar.
 * It does not see the lobby's own classes or the other libraries inside the lobby jar (database drivers,
 * Configurate, Logback...), so the lobby can change them without breaking addons, and an addon may bring
 * its own versions of such libraries in its jar.
 *
 * <p>This is about stable code, not security: an addon runs with the same rights as the server.
 */
public final class AddonClassLoader extends URLClassLoader {

    /** Packages addons get from the server instead of their own jar. */
    private static final List<String> SHARED_PACKAGES = List.of(
            "io.github.mohammadhadimohammadi2007_dot.lobby.api.",
            "net.minestom.",
            "net.kyori.",
            "org.slf4j.",
            "org.jetbrains.annotations.",
            "org.intellij.lang.annotations.",
            "com.google.gson.",
            "it.unimi.dsi.fastutil.");

    static {
        registerAsParallelCapable();
    }

    private final String addonId;
    private final ClassLoader server;
    private final List<AddonClassLoader> dependencies;

    /**
     * @param server       where the lobby's classes come from
     * @param dependencies the loaders of the addons this one depends on (hard and present soft dependencies)
     */
    public AddonClassLoader(String addonId, Path jar, ClassLoader server, List<AddonClassLoader> dependencies)
            throws IOException {
        super("addon-" + addonId, new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        this.addonId = addonId;
        this.server = server;
        this.dependencies = List.copyOf(dependencies);
    }

    public String addonId() {
        return addonId;
    }

    /** True if classes of this package come from the server. */
    static boolean shared(String className) {
        for (String prefix : SHARED_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = find(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> find(String name) throws ClassNotFoundException {
        // java.*, javax.* and the rest of the platform.
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException notPlatform) {
            // Next.
        }
        if (shared(name)) {
            return server.loadClass(name);
        }
        Class<?> own = findOwn(name);
        if (own != null) {
            return own;
        }
        for (AddonClassLoader dependency : dependencies) {
            Class<?> fromDependency = dependency.findOwn(name);
            if (fromDependency != null) {
                return fromDependency;
            }
        }
        throw new ClassNotFoundException(name + " (addon '" + addonId + "' can only use its own classes, the lobby API,"
                + " Minestom and the addons it depends on)");
    }

    /** A class from this addon's own jar, or {@code null}. */
    private @Nullable Class<?> findOwn(String name) {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return loaded;
            }
            try {
                return findClass(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }

    @Override
    public @Nullable URL getResource(String name) {
        // Only the addon's own files (its config defaults); the lobby's resources are not part of the API.
        return findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return findResources(name);
    }
}
