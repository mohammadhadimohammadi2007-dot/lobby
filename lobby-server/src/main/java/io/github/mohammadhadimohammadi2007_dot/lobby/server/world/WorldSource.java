package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Where the lobby map comes from, worked out from the {@code world.path} option.
 *
 * @param format        how to load it
 * @param path          the file or folder to load
 * @param convertTarget for {@link WorldFormat#ANVIL} with conversion on: the {@code .polar} file to create;
 *                      otherwise {@code null}
 * @param problem       for {@link WorldFormat#FLAT_FALLBACK}: why no map was loaded; otherwise {@code null}
 */
public record WorldSource(WorldFormat format, Path path, Path convertTarget, String problem) {

    private static final String POLAR_EXTENSION = ".polar";
    private static final String REGION_FOLDER = "region";
    /** Since Minecraft 26.1 the overworld lives in this sub folder of the world. */
    private static final Path MODERN_OVERWORLD = Path.of("dimensions", "minecraft", "overworld");

    /**
     * Detects the format of {@code path}.
     *
     * <ul>
     *   <li>{@code something.polar} file: Polar.</li>
     *   <li>Folder with {@code region/} (worlds saved before Minecraft 26.1) or with
     *       {@code dimensions/minecraft/overworld/region/} (26.1 and newer): Anvil. With conversion on, the {@code .polar} file is created next to
     *       the folder; if that file already exists and is newer than every region file, it is loaded instead
     *       (so editing the folder triggers a new conversion).</li>
     *   <li>A folder that does not exist, but a {@code .polar} file with the same name does: that file
     *       (the folder was converted and then removed).</li>
     *   <li>{@code something.polar} that does not exist, but a {@code something/} Anvil folder does:
     *       that Anvil folder (so dropping a vanilla world next to the default path just works).</li>
     *   <li>Anything else: flat fallback, with {@link #problem()} explaining why.</li>
     * </ul>
     */
    public static WorldSource detect(Path path, boolean convertAnvilToPolar) {
        if (Files.isRegularFile(path) && isPolarName(path)) {
            return new WorldSource(WorldFormat.POLAR, path, null, null);
        }
        if (isAnvilFolder(path)) {
            return anvil(path, convertAnvilToPolar);
        }
        if (isPolarName(path) && !Files.exists(path)) {
            Path anvilTwin = withoutPolarExtension(path);
            if (isAnvilFolder(anvilTwin)) {
                return anvil(anvilTwin, convertAnvilToPolar);
            }
        }
        if (!isPolarName(path) && !Files.exists(path) && Files.isRegularFile(polarFileFor(path))) {
            return new WorldSource(WorldFormat.POLAR, polarFileFor(path), null, null);
        }
        if (!Files.exists(path)) {
            return fallback(path, "'" + path + "' does not exist");
        }
        if (Files.isDirectory(path)) {
            return fallback(path, "'" + path + "' is a folder but has no 'region' folder inside, so it is not an Anvil world"
                    + " (point world.path at the world folder, the one that contains 'level.dat')");
        }
        return fallback(path, "'" + path + "' is a file but not a .polar file");
    }

    private static WorldSource anvil(Path folder, boolean convert) {
        if (!convert) {
            return new WorldSource(WorldFormat.ANVIL, folder, null, null);
        }
        Path polar = polarFileFor(folder);
        if (Files.isRegularFile(polar) && !changedSince(folder, polar)) {
            // Converted on an earlier start, and the folder was not edited since.
            return new WorldSource(WorldFormat.POLAR, polar, null, null);
        }
        return new WorldSource(WorldFormat.ANVIL, folder, polar, null);
    }

    /** True if any region file of {@code anvilWorld} is newer than {@code polar}. */
    static boolean changedSince(Path anvilWorld, Path polar) {
        try (Stream<Path> regions = Files.list(regionParent(anvilWorld).resolve(REGION_FOLDER))) {
            FileTime converted = Files.getLastModifiedTime(polar);
            return regions.anyMatch(file -> {
                try {
                    return Files.getLastModifiedTime(file).compareTo(converted) > 0;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            return false; // Cannot tell: keep using the converted file.
        }
    }

    private static WorldSource fallback(Path path, String problem) {
        return new WorldSource(WorldFormat.FLAT_FALLBACK, path, null, problem);
    }

    /** The {@code .polar} file created when converting {@code anvilFolder}: same name, next to it. */
    public static Path polarFileFor(Path anvilFolder) {
        Path absolute = anvilFolder.toAbsolutePath().normalize();
        return absolute.resolveSibling(absolute.getFileName() + POLAR_EXTENSION);
    }

    /**
     * The folder that directly contains {@code region/} for an Anvil world: the world itself for worlds
     * saved before Minecraft 26.1, or {@code dimensions/minecraft/overworld} for newer ones.
     */
    public static Path regionParent(Path anvilWorld) {
        return Files.isDirectory(anvilWorld.resolve(REGION_FOLDER)) ? anvilWorld : anvilWorld.resolve(MODERN_OVERWORLD);
    }

    /** True if the world uses the Minecraft 26.1+ folder layout. */
    public static boolean isModernLayout(Path anvilWorld) {
        return !Files.isDirectory(anvilWorld.resolve(REGION_FOLDER))
                && Files.isDirectory(anvilWorld.resolve(MODERN_OVERWORLD).resolve(REGION_FOLDER));
    }

    private static boolean isAnvilFolder(Path path) {
        return Files.isDirectory(path.resolve(REGION_FOLDER))
                || Files.isDirectory(path.resolve(MODERN_OVERWORLD).resolve(REGION_FOLDER));
    }

    private static boolean isPolarName(Path path) {
        Path name = path.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(POLAR_EXTENSION);
    }

    private static Path withoutPolarExtension(Path path) {
        String name = path.getFileName().toString();
        return path.resolveSibling(name.substring(0, name.length() - POLAR_EXTENSION.length()));
    }
}
