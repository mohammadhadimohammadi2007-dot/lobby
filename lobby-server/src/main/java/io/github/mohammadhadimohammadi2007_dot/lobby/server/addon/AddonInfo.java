package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import java.util.List;

/**
 * An addon as {@code /setup-lobby addons} and the doctor show it.
 *
 * @param detail why it is not running, or empty
 * @param jar    the jar's file name
 */
public record AddonInfo(String id, String name, String version, List<String> authors, AddonState state,
                        String detail, String jar) {

    public AddonInfo {
        authors = List.copyOf(authors);
    }
}
