package io.github.mohammadhadimohammadi2007_dot.lobby.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Release names to protocol versions, for options like {@code min-version}. */
class ProtocolVersionsTest {

    @Test
    void aReleaseNameGivesItsProtocol() {
        assertEquals(ProtocolVersions.V1_8, ProtocolVersions.fromName("1.8"));
        assertEquals(ProtocolVersions.V1_8, ProtocolVersions.fromName("1.8.9"));
        assertEquals(107, ProtocolVersions.fromName("1.9"));
        assertEquals(110, ProtocolVersions.fromName("1.9.4"));
        assertEquals(ProtocolVersions.V1_13, ProtocolVersions.fromName("1.13"));
        assertEquals(ProtocolVersions.V1_19_4, ProtocolVersions.fromName(" 1.19.4 "));
        assertEquals(ProtocolVersions.V1_20_5, ProtocolVersions.fromName("1.20.5"));
        assertEquals(ProtocolVersions.V1_20_5, ProtocolVersions.fromName("1.20.6"));
        assertEquals(775, ProtocolVersions.fromName("26.1"));
    }

    @Test
    void aReleaseBetweenKnownOnesGivesTheNextKnownOne() {
        // 1.10 is listed as 1.10.x, so 1.9.5 (which never existed) comes out as 1.10.
        assertEquals(210, ProtocolVersions.fromName("1.9.5"));
    }

    @Test
    void numbersAreProtocolsAndNonsenseIsRejected() {
        assertEquals(47, ProtocolVersions.fromName("47"));
        assertEquals(-1, ProtocolVersions.fromName("latest"));
        assertEquals(-1, ProtocolVersions.fromName("1"));
        assertEquals(-1, ProtocolVersions.fromName("99.0"));
        assertEquals(-1, ProtocolVersions.fromName(""));
    }
}
