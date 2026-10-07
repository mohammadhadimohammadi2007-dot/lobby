package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans;

/**
 * An active ban or mute read from LiteBans.
 *
 * @param id           row id in the LiteBans table
 * @param reason       why the player was punished
 * @param punishedBy   name of the staff member (or console)
 * @param untilMillis  expiry time in epoch milliseconds, or 0 / negative for permanent
 * @param ipBased      true for IP bans and IP mutes
 */
public record Punishment(long id, String reason, String punishedBy, long untilMillis, boolean ipBased) {

    /** True if the punishment never expires. */
    public boolean permanent() {
        return untilMillis < 1;
    }

    /** True if the punishment still applies at {@code nowMillis}. */
    public boolean activeAt(long nowMillis) {
        return permanent() || untilMillis > nowMillis;
    }
}
