package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.portal.Portal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Runs the {@link NetworkCheck} when the proxy bridge first reports the network (and whenever its group or
 * server names change) and after every {@code /lobby reload}, logs one warning if names are unknown, and
 * keeps the result for {@code /lobby info}.
 */
public final class NetworkCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkCheckService.class);

    /** Where the names come from; holograms, NPCs and portals are read when the check runs. */
    public record Sources(Supplier<List<HologramData>> holograms, Supplier<List<NpcData>> npcs,
                          Supplier<List<Portal>> portals) {
    }

    private final ConfigManager config;
    private final NetworkState network;
    private final Sources sources;
    private volatile NetworkCheck.Report last = NetworkCheck.Report.NOT_CHECKED;

    public NetworkCheckService(ConfigManager config, NetworkState network, Sources sources) {
        this.config = config;
        this.network = network;
        this.sources = sources;
    }

    /** Checks whenever the network's names change. The reload hook is added by the server. */
    public void start() {
        network.onNamesChanged(this::run);
    }

    /** Checks now and logs the result if something is unknown. Returns the report. */
    public synchronized NetworkCheck.Report run() {
        if (!network.hasSnapshot()) {
            last = NetworkCheck.Report.NOT_CHECKED;
            return last;
        }
        List<NetworkReference> references = NetworkReferences.collect(config.current(), sources.holograms().get(),
                sources.npcs().get(), sources.portals().get());
        NetworkCheck.Report report = NetworkCheck.check(references, network);
        if (!report.unknown().isEmpty()) {
            LOGGER.warn("{}", report.warning());
        }
        last = report;
        return report;
    }

    /** The last result, for {@code /lobby info}. */
    public NetworkCheck.Report last() {
        return last;
    }
}
