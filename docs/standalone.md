# Standalone setup

Standalone means players connect straight to the lobby, with no proxy. Use it for testing, for a
single-server setup, or to try the lobby before putting it in your network.

## 1. Start the server

```bash
java -Xms1G -Xmx1G -jar lobby-server.jar
```

The first start creates `config.yml`, `integrations.yml` and `messages.yml` next to the jar.
The defaults are already standalone:

```yaml
mode: standalone
online-mode: false
```

## 2. Choose online or offline mode

| `online-mode` | Who can join | Skins |
|---|---|---|
| `true` | Only premium (paid) accounts. Names and UUIDs are checked with Mojang. | Real skins |
| `false` | Anyone, with any name. | Fetched from Mojang by name (`fetch-skins-for-offline-players`), or from SkinsRestorer if enabled |

**Offline mode is insecure on a public port.** Anybody can join as anybody, including your staff
names. The console warns about this at every start. Use it only on `localhost`, on a private network,
or behind a proxy.

## 3. Add your map

Put a `.polar` file at `worlds/lobby.polar` (the default `world.path`), or set `world.path` to a
vanilla world folder. See [maps.md](maps.md). Restart the server.

## 4. Give yourself permissions

Add your name to `operators:` in `config.yml` and run `/lobby reload` from the console:

```yaml
operators: ["YourName"]
```

## Bans in standalone mode

With nobody else to check bans, the lobby checks them itself if the
[LiteBans integration](integrations/litebans.md) is enabled: banned players are refused at login with
the reason and expiry.

## Console

Type `stop` to shut down. Every command works in the console without the `/`, e.g. `lobby info`.
