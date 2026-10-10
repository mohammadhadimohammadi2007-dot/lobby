# Velocity setup (recommended)

This puts the lobby behind a [Velocity](https://papermc.io/software/velocity) proxy using
**modern forwarding**, which is secure and passes players' real UUIDs, names, IPs and skins to the lobby.

## 1. Configure Velocity

In `velocity.toml`:

```toml
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"

[servers]
lobby = "127.0.0.1:25566"
# your other servers...
try = ["lobby"]
```

Velocity creates `forwarding.secret` on its first start. Open it and copy the text inside.

## 2. Configure the lobby

In the lobby's `config.yml`:

```yaml
server:
  host: "127.0.0.1"   # only reachable from this machine; use the proxy's network if it runs elsewhere
  port: 25566

mode: velocity
velocity-secret: "paste the text from forwarding.secret here"
```

The lobby refuses to start if `mode` is `velocity` and the secret is empty.
`online-mode` is ignored behind a proxy (the proxy decides).

## 3. Install the bridge (optional, recommended)

The bridge plugin tells the lobby each player's **real client version** (important with ViaVersion)
and **live player counts** of all servers, which later phases use for the server selector,
scoreboards and holograms.

1. Copy `lobby-bridge.jar` into Velocity's `plugins/` folder and restart Velocity.
   It works on Velocity 3.5 (Java 21) and Velocity 4 (Java 25).
2. Edit `plugins/lobby-bridge/config.toml`:
   ```toml
   lobby-servers = ["lobby-1", "lobby-2"]   # names from velocity.toml; global and staff chat are shared between them
   update-interval = 2                      # seconds between player count updates
   status-interval = 10                     # seconds between online/offline pings
   allowed-commands = ["mute", "tempmute", "warn"]   # console commands lobbies may request (chat auto-mute)
   [groups]
   bedwars = ["bw-1", "bw-2"]
   ```
   A `lobbies` group with your `lobby-servers` is added automatically (used by `%lobby_count%`).
3. In the lobby's `integrations.yml`, set:
   ```yaml
   bridge:
     enabled: true
   ```
4. Restart the lobby. `/lobby info` shows `Bridge: connected (last update 1s ago)` once a player is on it.

### Checking the group names

The bundled menus use groups called `bedwars`, `skywars` and `duels`. If your `[groups]` use other names,
those items would show "offline" forever. So when the bridge first reports the network, whenever its group
or server names change, and after every `/lobby reload`, the lobby checks every group and server name it
uses: in menus, hotbar items, NPC names and actions, portals, holograms and `display.yml`. If any are
unknown, it logs one warning:

```
These names are not known to the proxy, so what uses them will show offline or fail to connect. ...
  - group 'bedwars', used in menus.yml: servers > slot 11
  - group 'duels', used in menus.yml: servers > slot 15
  The proxy has the groups: bw, lobbies, sw
  and the servers: bw-1, bw-2, lobby-1, sw-1
```

`/lobby info` shows the same under "Network names". Rename the groups in `[groups]`, or the names in the
lobby's files, so they match.

If ViaVersion is installed on the proxy, the bridge asks it for the real client version; otherwise it
uses Velocity's. Velocity's alone is not enough for old clients: with modern forwarding, ViaVersion
translates clients older than 1.13 to 1.13 before Velocity sees them (modern forwarding needs a login
message that only exists from 1.13 on), so Velocity reports 1.13 for a 1.8.9 player. The bridge says so in
the proxy log when it starts without ViaVersion. To check a player, have them run `/lobby info`: "Your
client" shows the version the lobby uses and whether the proxy reported it.

Without the bridge, every player counts as a modern client, all counts are 0, every
server shows as offline, global and staff chat stay on the local lobby, and chat auto-mute falls back to a
local temporary mute.

The bridge only ever runs console commands whose first word is in `allowed-commands`, and only when a lobby
server (never a player) asks. Every command it runs is written to the proxy log. Set `allowed-commands = []`
to turn this off completely. Wire format and security details: [bridge-protocol.md](bridge-protocol.md).

## Skins

Behind Velocity, skins arrive with the forwarded profile and are applied automatically. With
SkinsRestorer in proxy mode, its skins are forwarded too.

## Firewall

Even with modern forwarding, only the proxy needs to reach the lobby port. Bind it to `127.0.0.1`
or block it from the internet.
