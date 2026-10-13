package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/** Packs a test addon (classes of the tests' {@code testaddons} package) into a jar, as an addon developer would. */
final class AddonJars {

    private AddonJars() {
    }

    /** A jar with {@code main} (and its nested classes), an addon.json and extra files. */
    static Path addon(Path folder, String id, Class<?> main, String extraJson, Map<String, String> files)
            throws IOException {
        String json = "{\"id\": \"" + id + "\", \"name\": \"" + id + "\", \"version\": \"1.0\", \"main\": \""
                + main.getName() + "\", \"api-version\": \"0.2\"" + (extraJson.isEmpty() ? "" : ", " + extraJson) + "}";
        Path jar = folder.resolve(id + ".jar");
        Files.createDirectories(folder);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            write(out, "addon.json", json.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, String> file : files.entrySet()) {
                write(out, file.getKey(), file.getValue().getBytes(StandardCharsets.UTF_8));
            }
            Path classes = classFolder(main);
            String prefix = main.getSimpleName();
            try (Stream<Path> list = Files.list(classes)) {
                for (Path file : list.toList()) {
                    String name = file.getFileName().toString();
                    if (name.equals(prefix + ".class") || name.startsWith(prefix + "$")) {
                        write(out, main.getPackageName().replace('.', '/') + "/" + name, Files.readAllBytes(file));
                    }
                }
            }
        }
        return jar;
    }

    /** A jar with only the given files (for broken addons). */
    static Path raw(Path folder, String fileName, Map<String, String> files) throws IOException {
        Path jar = folder.resolve(fileName);
        Files.createDirectories(folder);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                write(out, file.getKey(), file.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        return jar;
    }

    private static void write(JarOutputStream out, String name, byte[] content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        ((OutputStream) out).write(content);
        out.closeEntry();
    }

    /** The folder the class file of {@code type} was compiled to. */
    private static Path classFolder(Class<?> type) throws IOException {
        try {
            Path root = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            return root.resolve(type.getPackageName().replace('.', '/'));
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }
}
