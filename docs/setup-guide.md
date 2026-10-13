# Setup guide: from zero to a network lobby

One page, in order. Every step ends with **Check it worked**; don't go on until it does. At any point,
`/setup-lobby setup` (in game or in the console) shows where you are, with ✔ / ⚠ / ✘ and the next command,
and `/setup-lobby doctor` explains what is wrong.

Steps 1-5 give you a working lobby on its own. Steps 6-10 put it in a network behind Velocity. Steps
11-13 fill it with ways into your games and get it running for good.

## 0. What you need

- **Java 25** on the machine (`java -version` must say 25).
- About 2 GB of memory for the lobby, 2 CPU cores (see [deployment.md](deployment.md#hardware); these are
  estimates from the load test).
- For a network: a [Velocity](https://papermc.io/software/velocity) proxy, and a MariaDB or MySQL database
  if you use LuckPerms, LiteBans or SkinsRestorer.

## 1. Get the jars

From the newest release: [`lobby-server.jar`](https://github.com/mohammadhadimohammadi2007-dot/lobby/releases/latest/download/lobby-server.jar) and
[`lobby-bridge.jar`](https://github.com/mohammadhadimohammadi2007-dot/lobby/releases/latest/download/lobby-bridge.jar). Or build them yourself:

```bash
git clone --recursive https://github.com/mohammadhadimohammadi2007-dot/lobby.git
cd lobby
./gradlew build
```

You get `lobby-server/build/libs/lobby-server.jar` (the lobby) and
`lobby-bridge-velocity/build/libs/lobby-bridge.jar` (the plugin for the proxy).

**Check it worked:** both jar files exist.

## 2. First start

Make a folder for the lobby, put `lobby-server.jar` and [`deploy/start.sh`](../deploy/start.sh) (or
`start.bat` on Windows) in it, and start it:

```bash
./start.sh
```

The first start writes every config file with comments, and makes a flat platform because there is no map
yet. It runs on its own (standalone, offline mode) with no edits.

**Check it worked:** the console ends with `Ready in ... ms. Listening on 0.0.0.0:25565`. Join
`localhost` with Minecraft (the version in the first log lines). Type `setup-` in the console and press
Tab: it completes.

## 3. Make yourself an operator

In the console:

```
op YourName
```

Rejoin. In offline mode a name proves nothing, so the console prints a one-time code:
`... tell them to type: /op confirm 482913`. Type that in game. ([Why](console-and-ops.md#offline-mode-protecting-operators).)

**Check it worked:** `/setup-lobby setup` works for you in game and shows the checklist.

## 4. Your map

Stop the server (`stop`). Copy your world folder (the one with `level.dat`) to `worlds/lobby` in the
lobby's folder. Start again. It is converted to Polar once (the original stays) and loaded completely.

**Check it worked:** the log says `Map 'lobby' ready: polar (... chunks preloaded: the whole map ...)`, and
`/setup-lobby setup` shows ✔ Map. Old clients only see blocks between Y 0 and 255: build there
([maps.md](maps.md)).

## 5. Spawn

Stand where players should appear, facing the way they should look:

```
/setup-lobby setspawn
```

**Check it worked:** rejoin; you appear there. `/setup-lobby setup` shows ✔ Spawn.

You now have a lobby on its own. For a public standalone server, read [standalone.md](standalone.md)
(online mode). For a network, go on.

## 6. Behind Velocity

On the proxy, in `velocity.toml` ([full example](../examples/network/velocity/velocity.toml)):

```toml
player-info-forwarding-mode = "modern"
[servers]
lobby-1 = "127.0.0.1:25566"
try = ["lobby-1"]
```

Start Velocity once so it writes `forwarding.secret`, and copy the text inside. In the lobby's
`config.yml` ([example](../examples/network/lobby/config.yml)):

```yaml
server:
  name: "lobby-1"
  host: "127.0.0.1"   # 0.0.0.0 if the proxy is on another machine, plus a firewall (step 10)
  port: 25566
mode: velocity
velocity-secret: "the text from forwarding.secret"
```

Restart the lobby and the proxy.

**Check it worked:** join the proxy's address. `/setup-lobby setup` shows ✔ Connection "behind Velocity"
and ✔ Proxy forwarding. If you are kicked with "Invalid proxy response" or "unable to verify player details", the secret differs;
`/setup-lobby doctor` from the console tells you what to compare.

Operators: behind a proxy the proxy checks names, so `op` works without a code from now on. Players get
their real UUIDs, so an operator from step 3 who had an offline UUID needs `op` again (the console says so
when they join without rights).

## 7. All client versions (ViaVersion)

On the **proxy** (not the lobby), install [ViaVersion](https://modrinth.com/plugin/viaversion),
[ViaBackwards](https://modrinth.com/plugin/viabackwards) and [ViaRewind](https://modrinth.com/plugin/viarewind),
and restart it.

**Check it worked:** join with 1.8.9 and with the latest version. With the bridge from step 8,
`/setup-lobby info` shows "Your client: 1.8.x (protocol 47), legacy, reported by the proxy" for the 1.8.9
player.

## 8. The bridge

Put `lobby-bridge.jar` in the proxy's `plugins/` folder and restart the proxy. Edit
`plugins/lobby-bridge/config.toml` ([example](../examples/network/velocity/plugins/lobby-bridge/config.toml)):

```toml
lobby-servers = ["lobby-1"]
[groups]
bedwars = ["bedwars-1", "bedwars-2"]
skywars = ["skywars-1"]
```

The names are exactly those of `velocity.toml` `[servers]`. In the lobby's `integrations.yml`:

```yaml
bridge:
  enabled: true
```

Restart the proxy and the lobby, then join.

**Check it worked:** `/setup-lobby setup` shows ✔ Proxy bridge "connected; groups: bedwars, lobbies,
skywars" and ✔ ViaVersion. `/setup-lobby info` shows "Bridge: connected".

## 9. Database, ranks, bans and skins (optional)

If LuckPerms, LiteBans or SkinsRestorer run on the proxy with a MariaDB/MySQL database, the lobby reads the
same database. In `integrations.yml` ([example](../examples/network/lobby/integrations.yml)) fill in
`database:` and turn on what you use; the table prefixes must match the plugins'
([what must match](../examples/network/README.md#settings-that-must-match)). Restart.

**Check it worked:** `/setup-lobby setup` shows ✔ for each one you turned on. A ✘ names the problem;
`/setup-lobby doctor` explains it (wrong password, database unreachable, tables missing...). Your
LuckPerms prefix shows in chat and the tab list.

## 10. Firewall

Only the proxy may reach the lobby's port. With `host: "127.0.0.1"` and the proxy on the same machine
nothing else can. Otherwise see [firewall.md](../examples/network/firewall.md).

**Check it worked:** from another machine, "Direct connection" to `<lobby ip>:25566` fails; through the
proxy it works.

## 11. Ways into your games

For each group, an NPC that sends players there. Stand where it should be:

```
/npc create bedwars
/npc skin bedwars Notch
/npc displayname bedwars <gold><bold>BedWars
/npc name bedwars addline <gray>%group_online_bedwars% playing
/npc action bedwars any_click add connect_group: bedwars
```

More: holograms ([holograms.md](holograms.md)), portals you walk through (made with the setup wand,
`/setup-lobby wand`; [movement-and-portals.md](movement-and-portals.md)), and the server selector menu
on the hotbar ([menus.md](menus.md), [hotbar.md](hotbar.md)).

**Check it worked:** clicking the NPC sends you to a BedWars server. `/setup-lobby setup` no longer lists
"Group bedwars: no NPC, portal, menu or hotbar item leads there", and `/setup-lobby doctor` reports no unknown names (a typo in a group
name shows up there).

## 12. Make it yours

- Scoreboard, tab list, nametags, boss bar: [display.md](display.md)
- Chat formats, filter, anti-spam: [chat.md](chat.md)
- What players may type: [commands.md](commands.md) (`commands.yml`)
- Server list text and player limit: `config.yml` `server:`
- A PvP area with kits and leaderboards: [pvp.md](pvp.md)

Every change applies with `/setup-lobby reload`, which also says if something needs a restart.

**Check it worked:** `/setup-lobby reload` answers "Configuration reloaded." without warnings.

## 13. Run it for good

Run the lobby as a service, in Pterodactyl/Pelican or in Docker: [deployment.md](deployment.md). For more
players, run a second lobby (another folder, `name: "lobby-2"`, port 25567, added to `lobby-servers` and
`try`) or several lobby instances on one server ([instances.md](instances.md)).

**Final check:** `/setup-lobby doctor` says "Nothing wrong found", and `/setup-lobby setup` is all ✔ (or
• for what you chose not to use).
