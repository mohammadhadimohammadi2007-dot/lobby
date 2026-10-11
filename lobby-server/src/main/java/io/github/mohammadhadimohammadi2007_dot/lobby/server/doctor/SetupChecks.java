package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The checks behind {@code /setup-lobby setup} (where the setup stands, step by step) and
 * {@code /setup-lobby doctor} (what is wrong, why, and how to fix it). Pure: only looks at {@link SetupFacts}.
 *
 * <p>Later features add their own setup checks with {@link #addSetupStep}.
 */
public final class SetupChecks {

    /** The proxy bridge reports nothing for this long: it is called silent. */
    static final long BRIDGE_SILENT_MILLIS = 60_000;
    /** After this long without any player, the doctor starts guessing why nobody gets in. */
    static final long NOBODY_JOINED_MILLIS = 5 * 60_000;
    /** The group every lobby server is in; it does not need an NPC. */
    private static final String LOBBIES_GROUP = "lobbies";

    private final List<Supplier<List<Check>>> extraSteps = new ArrayList<>();

    /** Adds checks of a feature (parkour, PvP...) to the end of {@code /setup-lobby setup}. */
    public void addSetupStep(Supplier<List<Check>> step) {
        extraSteps.add(step);
    }

    /** The setup checklist, in the order an admin works through it. */
    public List<Check> setup(SetupFacts facts) {
        List<Check> checks = new ArrayList<>();
        checks.add(map(facts));
        checks.add(spawn(facts));
        checks.add(operators(facts));
        checks.add(connection(facts));
        if (facts.behindProxy()) {
            checks.add(forwarding(facts));
            checks.add(bridge(facts));
            checks.add(viaVersion(facts));
        }
        for (IntegrationStatus integration : facts.integrations()) {
            checks.add(integration(integration));
        }
        checks.addAll(groupsWithoutWay(facts));
        checks.add(serverSelector(facts));
        checks.add(facts.sidebarEnabled() && facts.tabEnabled()
                ? Check.ok("Scoreboard and tab list", "on (display.yml)")
                : Check.info("Scoreboard and tab list", (facts.sidebarEnabled() ? "the tab list" : "the scoreboard")
                + " is off in display.yml", null));
        checks.add(facts.commandsWhitelist()
                ? Check.ok("Command list", "players only see the commands commands.yml lists for their group")
                : Check.info("Command list", "commands.yml is off: players see every command their permissions allow",
                null));
        for (Supplier<List<Check>> step : extraSteps) {
            checks.addAll(step.get());
        }
        return checks;
    }

    private static Check map(SetupFacts facts) {
        if (facts.worldFormat() == WorldFormat.FLAT_FALLBACK) {
            return Check.fail("Map", "no map at '" + facts.worldPath() + "', so a flat platform was made. Copy your"
                    + " world folder (the one with level.dat) there and restart.", null);
        }
        return Check.ok("Map", "'" + facts.worldName() + "' (" + facts.worldFormat().name().toLowerCase(Locale.ROOT)
                + ", " + facts.chunks() + " chunks)");
    }

    private static Check spawn(SetupFacts facts) {
        if (facts.spawnIsDefault() && facts.worldFormat() != WorldFormat.FLAT_FALLBACK) {
            return Check.warn("Spawn", "still the default (0.5, 65, 0.5). Stand where players should appear and run"
                    + " this:", "/setup-lobby setspawn");
        }
        if (!facts.spawnOnGround()) {
            return Check.warn("Spawn", "there is no solid block under the spawn, so players fall. Stand on the ground"
                    + " and run this:", "/setup-lobby setspawn");
        }
        return Check.ok("Spawn", "set");
    }

    private static Check operators(SetupFacts facts) {
        return facts.operators() == 0
                ? Check.warn("Operators", "nobody is an operator yet. In the console: op <your name>", "/op ")
                : Check.ok("Operators", facts.operators() + " in data/ops.yml");
    }

    private static Check connection(SetupFacts facts) {
        return switch (facts.mode()) {
            case STANDALONE -> facts.onlineMode()
                    ? Check.ok("Connection", "standalone, online mode (Mojang checks names)")
                    : Check.warn("Connection", "standalone, offline mode: anyone can join with any name. Fine for"
                    + " testing; for a network set mode: velocity in config.yml.", null);
            case VELOCITY -> facts.velocitySecretSet()
                    ? Check.ok("Connection", "behind Velocity (modern forwarding)")
                    : Check.fail("Connection", "mode is velocity but velocity-secret is empty. Copy the secret from"
                    + " forwarding.secret on the proxy into config.yml.", null);
            case BUNGEECORD -> facts.bungeeGuardTokens() > 0
                    ? Check.ok("Connection", "behind BungeeCord with BungeeGuard")
                    : Check.warn("Connection", "behind BungeeCord without BungeeGuard tokens: if the lobby port is"
                    + " reachable from the internet, anyone can pretend to be anyone. Firewall the port, or use"
                    + " BungeeGuard (bungeeguard-tokens) or Velocity.", null);
        };
    }

    private static Check forwarding(SetupFacts facts) {
        return facts.playersJoined() > 0
                ? Check.ok("Proxy forwarding", "players arrive through the proxy")
                : Check.info("Proxy forwarding", "no player has joined yet. Join through the proxy to test it.", null);
    }

    private static Check bridge(SetupFacts facts) {
        if (!facts.bridgeEnabled()) {
            return Check.warn("Proxy bridge", "off in integrations.yml (bridge.enabled). Without it player counts stay"
                    + " 0, servers show offline and 1.8 clients are not recognized.", null);
        }
        if (!facts.bridgeSeen()) {
            return facts.playersJoined() > 0
                    ? Check.fail("Proxy bridge", "players have joined, but the bridge never answered. Run the doctor.",
                    "/setup-lobby doctor")
                    : Check.info("Proxy bridge", "waiting for the first player (the bridge talks through a player's"
                    + " connection)", null);
        }
        return Check.ok("Proxy bridge", "connected; groups: " + (facts.groups().isEmpty() ? "none"
                : String.join(", ", new TreeSet<>(facts.groups().keySet()))));
    }

    private static Check viaVersion(SetupFacts facts) {
        if (facts.proxy().isEmpty()) {
            return facts.bridgeSeen()
                    ? Check.warn("ViaVersion", "the bridge on the proxy is older than 0.4 and does not report it."
                    + " Update lobby-bridge.jar.", null)
                    : Check.info("ViaVersion", "not known until the bridge answers", null);
        }
        BridgeMessage.ProxyInfo proxy = facts.proxy().get();
        if (!proxy.viaVersion()) {
            return Check.warn("ViaVersion", "not on the proxy (" + proxy.proxyVersion() + "): only clients of the"
                    + " lobby's own version can join, and the lobby cannot tell 1.8 players apart.", null);
        }
        String missing = !proxy.viaBackwards() ? " ViaBackwards is missing (clients 1.9 to 1.20 cannot join)."
                : !proxy.viaRewind() ? " ViaRewind is missing (1.8 clients cannot join)." : "";
        return missing.isEmpty()
                ? Check.ok("ViaVersion", "ViaVersion, ViaBackwards and ViaRewind on " + proxy.proxyVersion())
                : Check.warn("ViaVersion", "on the proxy." + missing, null);
    }

    private static Check integration(IntegrationStatus integration) {
        return switch (integration.state()) {
            case ACTIVE -> Check.ok(integration.name(), integration.detail());
            case FAILED -> Check.fail(integration.name(), integration.detail() + ". Run the doctor.", "/setup-lobby doctor");
            case DISABLED -> Check.info(integration.name(), "off (integrations.yml)", null);
        };
    }

    /** Server groups nothing in the lobby leads to. */
    private static List<Check> groupsWithoutWay(SetupFacts facts) {
        List<Check> checks = new ArrayList<>();
        for (String group : new TreeSet<>(facts.groups().keySet())) {
            if (group.equals(LOBBIES_GROUP) || facts.referencedGroups().contains(group)) {
                continue;
            }
            checks.add(Check.info("Group " + group, "no NPC, portal, menu or hotbar item leads there yet. Stand where"
                    + " it should be and create an NPC that joins it:", "/npc create " + group));
        }
        return checks;
    }

    private static Check serverSelector(SetupFacts facts) {
        if (!facts.menus().contains("servers")) {
            return Check.info("Server selector", "there is no 'servers' menu in menus.yml", null);
        }
        if (facts.network().checked() && !facts.network().unknown().isEmpty()) {
            return Check.warn("Server selector", facts.network().summary(), "/setup-lobby doctor");
        }
        return Check.ok("Server selector", "the 'servers' menu exists" + (facts.network().checked()
                ? " and every group and server name is known to the proxy" : ""));
    }

    // ---------------- Doctor ----------------

    /** Every problem found, each with the likely cause and the fix. Only an OK line if nothing is wrong. */
    public List<Check> doctor(SetupFacts facts) {
        List<Check> problems = new ArrayList<>();
        // What the checklist calls broken (no map, no velocity secret...), unless explained in more detail below.
        Check map = map(facts);
        if (map.status() == Check.Status.FAIL) {
            problems.add(map);
        }
        Check spawn = spawn(facts);
        if (!facts.spawnOnGround()) {
            problems.add(spawn);
        }
        if (facts.databaseError() != null) {
            problems.add(Check.fail("Database", facts.databaseError() + " " + databaseHint(facts.databaseError()), null));
        }
        for (IntegrationStatus integration : facts.integrations()) {
            if (integration.state() == IntegrationStatus.State.FAILED) {
                problems.add(Check.fail(integration.name(), integration.detail() + integrationHint(integration), null));
            }
        }
        if (facts.mode() == ConnectionMode.VELOCITY && !facts.velocitySecretSet()) {
            problems.add(Check.fail("Velocity secret", "velocity-secret in config.yml is empty. Copy the text of"
                    + " forwarding.secret (next to velocity.toml) into it, and make sure velocity.toml has"
                    + " player-info-forwarding-mode = \"modern\".", null));
        }
        if (facts.behindProxy() && facts.playersJoined() == 0 && facts.uptimeMillis() > NOBODY_JOINED_MILLIS) {
            problems.add(Check.warn("Nobody joined yet", "if players are kicked with \"Invalid proxy response\" or"
                    + " \"unable to verify player details\", the forwarding secret differs: compare forwarding.secret"
                    + " on the proxy with velocity-secret here. If they never reach the lobby, check that velocity.toml"
                    + " lists this server under [servers] with this port, and that the lobby is in try = [...].", null));
        }
        if (facts.behindProxy() && facts.bridgeEnabled() && !facts.bridgeSeen() && facts.playersJoined() > 0) {
            problems.add(Check.fail("Proxy bridge", "players have joined but the bridge never sent anything. Check"
                    + " on the proxy: lobby-bridge.jar is in plugins/ and loaded (/velocity plugins); this server's name"
                    + " from velocity.toml [servers] is in lobby-servers in plugins/lobby-bridge/config.toml; and the"
                    + " proxy was restarted after editing it.", null));
        }
        if (facts.bridgeSeen() && facts.bridgeSilentMillis() > BRIDGE_SILENT_MILLIS) {
            problems.add(Check.warn("Proxy bridge", "nothing from the bridge for " + facts.bridgeSilentMillis() / 1000
                    + " seconds. It only talks while players are on this lobby; if there are, check the proxy log.",
                    null));
        }
        facts.proxy().ifPresent(proxy -> {
            if (!proxy.viaVersion() && facts.mode() == ConnectionMode.VELOCITY) {
                problems.add(Check.warn("ViaVersion", "not installed on the proxy. With modern forwarding only clients"
                        + " of the lobby's own version can join; install ViaVersion (and ViaBackwards and ViaRewind for"
                        + " older clients) on the proxy.", null));
            }
            if (!proxy.onlineMode()) {
                problems.add(Check.warn("Proxy in offline mode", "the proxy does not check names with Mojang. If that"
                        + " is on purpose, set ops.protect-behind-proxy: true in config.yml so operators need a code.",
                        null));
            }
        });
        if (facts.network().checked() && !facts.network().unknown().isEmpty()) {
            problems.add(Check.warn("Network names", facts.network().warning(), null));
        }
        for (Map.Entry<String, List<String>> broken : new java.util.TreeMap<>(facts.brokenEntries()).entrySet()) {
            if (!broken.getValue().isEmpty()) {
                problems.add(Check.warn(broken.getKey(), broken.getValue().size() + " entr"
                        + (broken.getValue().size() == 1 ? "y" : "ies") + " could not be read and are skipped (kept in"
                        + " the file): " + String.join(", ", broken.getValue()) + ". The console log at startup says"
                        + " why; fix them and run /setup-lobby reload.", null));
            }
        }
        List<String> legacy = facts.configWarnings().stream().filter(w -> w.contains("1.8")).toList();
        if (!legacy.isEmpty()) {
            problems.add(Check.info("1.8 clients", legacy.size() + " thing(s) look different on 1.8, e.g. "
                    + legacy.getFirst() + (legacy.size() > 1 ? " (all of them are in the startup log)" : ""), null));
        }
        long otherWarnings = facts.configWarnings().stream().filter(w -> !w.contains("1.8")).count();
        if (otherWarnings > 0) {
            problems.add(Check.warn("Config files", otherWarnings + " problem(s) in the config files, e.g. "
                    + facts.configWarnings().stream().filter(w -> !w.contains("1.8")).findFirst().orElse("")
                    + ". Every one is in the startup log and after /setup-lobby reload.", null));
        }
        if (problems.isEmpty()) {
            problems.add(Check.ok("Nothing wrong found", "run /setup-lobby setup to see what is not set up yet"));
        }
        return problems;
    }

    /** What to check for a database error, from the driver's message. */
    static String databaseHint(String error) {
        String lower = error.toLowerCase(Locale.ROOT);
        if (lower.contains("access denied")) {
            return "Check username and password in integrations.yml, and that the user may connect from this"
                    + " machine (in MariaDB: GRANT ... TO 'user'@'%').";
        }
        if (lower.contains("unknown database")) {
            return "Create the database first (CREATE DATABASE ...) or fix database: in integrations.yml.";
        }
        if (lower.contains("refused") || lower.contains("timed out") || lower.contains("timeout")
                || lower.contains("unknown host") || lower.contains("no route")) {
            return "The database server cannot be reached: check host and port in integrations.yml, that MariaDB or"
                    + " MySQL is running, listens on that address (bind-address) and that a firewall lets this machine"
                    + " in.";
        }
        return "Check host, port, database, username and password in integrations.yml.";
    }

    private static String integrationHint(IntegrationStatus integration) {
        String lower = integration.detail().toLowerCase(Locale.ROOT);
        if (lower.contains("table") || lower.contains("doesn't exist")) {
            return ". Its tables are missing: check table-prefix in integrations.yml against the plugin's own config on"
                    + " the proxy, and that the plugin has started there at least once (it creates the tables).";
        }
        if (lower.contains("no database connection")) {
            return ". Fix the database connection first.";
        }
        return "";
    }
}
