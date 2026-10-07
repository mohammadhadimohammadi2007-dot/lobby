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
   lobby-servers = ["lobby"]   # names from velocity.toml
   update-interval = 2
   [groups]
   bedwars = ["bw-1", "bw-2"]
   ```
3. In the lobby's `integrations.yml`, set:
   ```yaml
   bridge:
     enabled: true
   ```
4. Restart the lobby. `/lobby info` shows `Bridge: connected (last update 1s ago)` once a player is on it.

If ViaVersion is installed on the proxy, the bridge asks it for the real client version; otherwise it
uses Velocity's. Without the bridge, every player counts as a modern client and all counts are 0.

## Skins

Behind Velocity, skins arrive with the forwarded profile and are applied automatically. With
SkinsRestorer in proxy mode, its skins are forwarded too.

## Firewall

Even with modern forwarding, only the proxy needs to reach the lobby port. Bind it to `127.0.0.1`
or block it from the internet.
