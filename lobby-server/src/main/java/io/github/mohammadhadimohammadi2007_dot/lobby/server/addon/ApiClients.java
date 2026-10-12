package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Clients;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import net.minestom.server.entity.Player;

/** Client versions for addons, as the bridge reports them. */
final class ApiClients implements Clients {

    private final BridgeService bridge;

    ApiClients(BridgeService bridge) {
        this.bridge = bridge;
    }

    @Override
    public int protocolVersion(Player player) {
        return bridge.capabilities(player).protocolVersion();
    }

    @Override
    public String versionName(Player player) {
        return ProtocolVersions.name(protocolVersion(player));
    }

    @Override
    public boolean legacy(Player player) {
        return bridge.capabilities(player).legacy();
    }

    @Override
    public boolean reported(Player player) {
        return bridge.capabilities(player).reported();
    }
}
