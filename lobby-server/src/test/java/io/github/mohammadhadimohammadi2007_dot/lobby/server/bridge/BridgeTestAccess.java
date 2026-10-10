package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;

import java.util.UUID;

/** Lets tests in other packages feed the bridge what a proxy would send. */
public final class BridgeTestAccess {

    private BridgeTestAccess() {
    }

    /** As if the proxy reported the player's real client version. */
    public static void reportVersion(BridgeService bridge, UUID player, int protocolVersion) {
        bridge.receive(BridgeCodec.encode(new BridgeMessage.ClientVersion(player, protocolVersion)), "test");
    }
}
