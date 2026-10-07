# BungeeCord / Waterfall setup

Legacy (BungeeCord) forwarding works, but it is **not secure on its own**: the lobby has to trust
whoever connects to its port. Prefer [Velocity](velocity.md) if you can.

## 1. Configure the proxy

In BungeeCord's `config.yml`:

```yaml
ip_forward: true
servers:
  lobby:
    address: 127.0.0.1:25566
```

## 2. Configure the lobby

```yaml
server:
  host: "127.0.0.1"
  port: 25566

mode: bungeecord
```

## 3. Protect the port (required)

With legacy forwarding, anyone who can reach the lobby port directly can join **as any player**.
You must do at least one of these:

- Bind the lobby to `127.0.0.1` (as above) when the proxy runs on the same machine.
- Firewall the port so only the proxy's IP can connect.
- Install [BungeeGuard](https://www.spigotmc.org/resources/bungeeguard.79601/) on the proxy and put
  its token in the lobby config:
  ```yaml
  bungeeguard-tokens: ["the-token-from-bungeeguard"]
  ```

The console warns about this at every start in `bungeecord` mode.

## Bridge

The bridge plugin is Velocity-only. Without it, the lobby treats every player as a modern client and
shows 0 for other servers' player counts.
