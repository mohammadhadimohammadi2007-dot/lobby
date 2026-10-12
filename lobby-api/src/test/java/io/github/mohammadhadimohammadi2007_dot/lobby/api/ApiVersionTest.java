package io.github.mohammadhadimohammadi2007_dot.lobby.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiVersionTest {

    @Test
    void currentMatchesTheBuild() {
        assertEquals(System.getProperty("lobby.api.version"), ApiVersion.CURRENT,
                "keep ApiVersion.CURRENT equal to the version in lobby-api/build.gradle.kts");
    }

    @Test
    void beforeOnePointZeroTheMinorVersionMustMatch() {
        assertTrue(ApiVersion.supports("0.1.0", "0.1"));
        assertTrue(ApiVersion.supports("0.1.3", "0.1.0"));
        assertFalse(ApiVersion.supports("0.2.0", "0.1"));
        assertFalse(ApiVersion.supports("0.1.0", "0.2"));
    }

    @Test
    void fromOnePointZeroOlderMinorVersionsWork() {
        assertTrue(ApiVersion.supports("1.3.0", "1.0"));
        assertTrue(ApiVersion.supports("1.3.0", "1.3"));
        assertFalse(ApiVersion.supports("1.3.0", "1.4"));
        assertFalse(ApiVersion.supports("2.0.0", "1.0"));
        assertTrue(ApiVersion.supports("1.0.0", "1"));
    }

    @Test
    void textThatIsNoVersionIsNotSupported() {
        assertFalse(ApiVersion.supports("0.1.0", "latest"));
        assertFalse(ApiVersion.supports("0.1.0", ""));
        assertFalse(ApiVersion.supports("0.1.0", "-1.0"));
    }
}
