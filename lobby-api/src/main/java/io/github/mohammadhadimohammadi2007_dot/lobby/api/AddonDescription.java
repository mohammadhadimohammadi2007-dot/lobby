package io.github.mohammadhadimohammadi2007_dot.lobby.api;

import java.util.List;
import java.util.Objects;

/**
 * What an addon's {@code addon.json} says about it:
 * <pre>{@code
 * {
 *   "id": "parkour-tools",
 *   "name": "Parkour Tools",
 *   "version": "1.2.0",
 *   "main": "com.example.parkour.ParkourAddon",
 *   "api-version": "0.1",
 *   "authors": ["Example"],
 *   "depends": ["economy-core"],
 *   "soft-depends": ["stats"]
 * }
 * }</pre>
 *
 * @param id          unique, lowercase letters, digits and {@code -}; also the name of the data folder
 * @param name        shown in {@code /setup-lobby addons}
 * @param version     the addon's own version, free text
 * @param mainClass   the class implementing {@link LobbyAddon}
 * @param apiVersion  the API version it was written for, see {@link ApiVersion}
 * @param authors     may be empty
 * @param depends     addons that must be present and are enabled first
 * @param softDepends addons that are enabled first if they are present
 */
public record AddonDescription(String id, String name, String version, String mainClass, String apiVersion,
                               List<String> authors, List<String> depends, List<String> softDepends) {

    /** What an addon id may look like. */
    public static final String ID_PATTERN = "[a-z0-9][a-z0-9-]{0,63}";

    public AddonDescription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(mainClass, "mainClass");
        Objects.requireNonNull(apiVersion, "apiVersion");
        authors = List.copyOf(authors);
        depends = List.copyOf(depends);
        softDepends = List.copyOf(softDepends);
    }
}
