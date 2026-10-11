package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import net.minestom.server.coordinate.CoordConversion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which chunks an Anvil world has, read from the headers of its region files ({@code r.<x>.<z>.mca}). Each
 * header lists where the region's 1024 chunks are; an empty entry means the chunk was never saved.
 */
final class AnvilRegions {

    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int CHUNKS_PER_SIDE = 32;
    private static final int HEADER_BYTES = 4096;

    private AnvilRegions() {
    }

    /** Chunk keys of every saved chunk in {@code regionFolder}. Broken or short region files are skipped. */
    static List<Long> storedChunks(Path regionFolder) throws IOException {
        List<Long> keys = new ArrayList<>();
        if (!Files.isDirectory(regionFolder)) {
            return keys;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(regionFolder, "r.*.mca")) {
            for (Path file : files) {
                Matcher matcher = REGION.matcher(file.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                int regionX = Integer.parseInt(matcher.group(1));
                int regionZ = Integer.parseInt(matcher.group(2));
                byte[] header = new byte[HEADER_BYTES];
                try (InputStream in = Files.newInputStream(file)) {
                    if (in.readNBytes(header, 0, HEADER_BYTES) < HEADER_BYTES) {
                        continue;
                    }
                }
                ByteBuffer locations = ByteBuffer.wrap(header);
                for (int index = 0; index < CHUNKS_PER_SIDE * CHUNKS_PER_SIDE; index++) {
                    if (locations.getInt(index * 4) != 0) {
                        keys.add(CoordConversion.chunkIndex(regionX * CHUNKS_PER_SIDE + index % CHUNKS_PER_SIDE,
                                regionZ * CHUNKS_PER_SIDE + index / CHUNKS_PER_SIDE));
                    }
                }
            }
        }
        return keys;
    }
}
