# Example network

A complete, working setup: one Velocity proxy, two lobbies, BedWars and SkyWars servers, an ajParkour
server, and LuckPerms, LiteBans and SkinsRestorer sharing one MariaDB database. Copy what you need; the
step-by-step version is [docs/setup-guide.md](../../docs/setup-guide.md).

```
                         players (1.8.9 ... latest)
                                   |
                     Velocity :25565  (ViaVersion, ViaBackwards, ViaRewind,
                                       lobby-bridge, LuckPerms, LiteBans, SkinsRestorer)
            ___________________|____________________________________
           |            |           |            |            |
     lobby-1 :25566  lobby-2 :25567  bedwars-1/2  skywars-1   parkour :25580
     (this project)  (this project)  (Paper...)   (Paper...)  (Paper + ajParkour)
                                   |
                         MariaDB :3306  (database "minecraft")
```

| File | Goes to |
|---|---|
| [velocity/velocity.toml](velocity/velocity.toml) | merge into the proxy's `velocity.toml` |
| [velocity/plugins/lobby-bridge/config.toml](velocity/plugins/lobby-bridge/config.toml) | the proxy's `plugins/lobby-bridge/config.toml` |
| [lobby/config.yml](lobby/config.yml) | the options to change in each lobby's `config.yml` (`name` and `port` differ per lobby) |
| [lobby/integrations.yml](lobby/integrations.yml) | each lobby's `integrations.yml` |
| [firewall.md](firewall.md) | firewall rules for the lobby ports |

Both example files are loaded by the lobby's and the bridge's own tests, so they always match the current
config format.

## Plugins on the proxy

| Plugin | Why |
|---|---|
| [ViaVersion](https://modrinth.com/plugin/viaversion) | Newer clients than the lobby's version, and the real client version for the bridge |
| [ViaBackwards](https://modrinth.com/plugin/viabackwards) | Older clients down to 1.9 |
| [ViaRewind](https://modrinth.com/plugin/viarewind) | 1.8.x clients |
| `lobby-bridge.jar` (this project) | Player counts, server status, client versions, server switching for the lobbies |
| LuckPerms (Velocity) | Ranks; the lobby reads the same database |
| LiteBans (Velocity) | Bans and mutes; the lobby reads mutes from the same database |
| SkinsRestorer (Velocity) | Skins; the lobby reads the same database |

Install the Via plugins **only on the proxy**, not on the lobbies (Minestom cannot run plugins).

## Settings that must match

| Where | Setting | Must equal |
|---|---|---|
| lobby `config.yml` `velocity-secret` | the text inside `forwarding.secret` | next to `velocity.toml` |
| lobby `config.yml` `server.port` | the port in `velocity.toml` `[servers]` | |
| bridge `config.toml` `lobby-servers` | the lobby names in `velocity.toml` `[servers]` | |
| bridge `config.toml` `[groups]` | the server names in `velocity.toml` `[servers]` | |
| lobby `integrations.yml` `database` | the database LuckPerms, LiteBans and SkinsRestorer use on the proxy | |
| lobby `luckperms.table-prefix` | `table-prefix` in LuckPerms' `config.yml` on the proxy (and every server) | `luckperms_` |
| lobby `luckperms.messaging-service` | `messaging-service` in LuckPerms' `config.yml` | `sql` |
| lobby `litebans.table-prefix` | `table_prefix` in LiteBans' `config.yml` | `litebans_` |
| lobby `skinsrestorer.table-prefix` | the table prefix in SkinsRestorer's database settings | `sr_` |

LuckPerms on the proxy must use `storage-method: mysql` (or `mariadb`) with the same database, not the
default H2 file, or the lobby sees no ranks.

## Check it worked

1. Start MariaDB, the proxy, then the lobbies.
2. Join through the proxy and run `/setup-lobby setup` on a lobby (as an operator): everything up to
   "Proxy bridge" and "ViaVersion" is ✔.
3. `/setup-lobby doctor` says "Nothing wrong found".
