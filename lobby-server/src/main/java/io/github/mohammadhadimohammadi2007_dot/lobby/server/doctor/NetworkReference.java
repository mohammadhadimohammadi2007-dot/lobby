package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

/**
 * A server or group of the network that the lobby's configuration names somewhere, for example in a
 * {@code connect_group: bedwars} action or a {@code %group_online_bedwars%} placeholder.
 *
 * @param where a short place for an admin to look, e.g. {@code menus.yml: servers > bedwars-online}
 */
public record NetworkReference(Kind kind, String name, String where) {

    /** What the name is. */
    public enum Kind {
        GROUP,
        SERVER;

        /** For messages: {@code group} or {@code server}. */
        public String word() {
            return this == GROUP ? "group" : "server";
        }
    }
}
