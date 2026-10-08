# Lobby

[![Build](https://github.com/mohammadhadimohammadi2007-dot/lobby/actions/workflows/build.yml/badge.svg)](https://github.com/mohammadhadimohammadi2007-dot/lobby/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25-orange)
![Minestom](https://img.shields.io/badge/Minestom-2026.10.05--26.2-purple)
![Clients](https://img.shields.io/badge/clients-1.8.9%20to%20latest-brightgreen)

A fast, open-source Minecraft **lobby server** built on [Minestom](https://minestom.net).
It is a standalone server (not a Paper plugin) made to sit behind a Velocity proxy as the hub of a
network, and it also runs completely on its own for testing or small servers.

- Boots with **zero configuration**: download, run, join `localhost`.
- Simple, commented config files written for server owners, not programmers.
- Built for **200+ players per lobby** (the lobby world is read-only and fully in memory).
- Players on **1.8.9 through the latest version** can join through ViaVersion on your proxy.
- A complete **chat system** with a filter that sees through spelling tricks and proper **Persian/Arabic** display.

> Status: **Phase 2 of 5** (placeholders and chat) done. See the [roadmap](#roadmap).

## Features

| Area | What you get |
|---|---|
| Connection | Standalone (online or offline mode), Velocity modern forwarding, BungeeCord legacy forwarding (+ BungeeGuard) |
| World | Loads `.polar` files and Anvil worlds (old and 26.1+ layouts), converts Anvil to Polar once, preloads everything, computes lighting if missing, never crashes on a missing map (flat platform fallback) |
| Lobby | Spawn on join, adventure mode, void teleport, fixed time, no weather, protections (break, place, damage, hunger, drop) |
| Commands | `/spawn`, `/lobby reload`, `/lobby setspawn`, `/lobby info` |
| Integrations (optional) | MariaDB pool, LuckPerms (live rank changes through SQL messaging), LiteBans (bans + mutes, read only), SkinsRestorer (read only), offline-mode skins from Mojang |
| Bridge | Velocity plugin: each player's real client version, live player counts and online/offline status per server, chat between lobbies, safe proxy commands (allowlist) |
| Placeholders | PlaceholderAPI-style `%player_name%`, `%luckperms_prefix%`, `%server_online%`, `%bungee_total%`... in messages and the MOTD, with an API for your own ([list](docs/placeholders.md)) |
| Chat | Rank formats (modern + 1.8 versions), channels shared between lobbies, anti-spam, word/link/IP filter that catches leetspeak, look-alike letters and Persian spelling tricks, Persian/Arabic letter joining and right-to-left order, mentions, emojis, ignore, staff tools (clear, lock, slow mode, delete, spy), chat log ([guide](docs/chat.md)) |

## Quick start (standalone, about a minute)

You need **Java 25**.

1. Download `lobby-server.jar` from the [releases page](../../releases) (or [build it](#building-from-source)).
2. Put it in an empty folder and run:
   ```bash
   java -Xms1G -Xmx1G -jar lobby-server.jar
   ```
3. Join `localhost` in Minecraft.

On the first start the server creates `config.yml`, `integrations.yml` and `messages.yml`.
No map yet? It generates a small platform so you can still join. Put your map at `worlds/lobby.polar`
(or point `world.path` at a vanilla world folder) and restart.

Type `stop` in the console to shut the server down.

## Configuration overview

| File | What it controls | Reload |
|---|---|---|
| `config.yml` | Port, MOTD, max players, connection mode, world, spawn, protections, operators | `/lobby reload` (it tells you if something needs a restart) |
| `integrations.yml` | Database, LuckPerms, LiteBans, SkinsRestorer, bridge | Restart |
| `chat.yml` | Chat formats, channels, anti-spam, filter, Persian display, mentions, broadcasts | `/lobby reload` |
| `filters/` | Blocked, censored and allowed word lists | `/lobby reload` |
| `messages.yml` | Every message players see ([MiniMessage](https://docs.advntr.dev/minimessage/format) format, with [placeholders](docs/placeholders.md)) | `/lobby reload` |

Every option has a comment above it. Missing options use the default (with a warning in the console),
and invalid values are reported with the option name and the allowed values.

## Permissions

| Permission | Allows |
|---|---|
| `lobby.command.spawn` | `/spawn` (everyone has it when LuckPerms is off) |
| `lobby.command.reload` | `/lobby reload` |
| `lobby.command.setspawn` | `/lobby setspawn` |
| `lobby.command.info` | `/lobby info` |
| `lobby.bypass.protection` | Ignore the protection settings |

Chat permissions are listed in the [chat guide](docs/chat.md#permissions).

Without LuckPerms, players listed under `operators:` in `config.yml` have every permission.

## Guides

- [Standalone setup](docs/standalone.md)
- [Velocity setup](docs/velocity.md) (recommended for networks)
- [BungeeCord / Waterfall setup](docs/bungeecord.md)
- [Maps: Polar, Anvil and conversion](docs/maps.md)
- [Supported client versions](docs/client-versions.md)
- [Chat](docs/chat.md)
- [Placeholders](docs/placeholders.md)
- Integrations: [Database](docs/integrations/database.md) ·
  [LuckPerms](docs/integrations/luckperms.md) ·
  [LiteBans](docs/integrations/litebans.md) ·
  [SkinsRestorer](docs/integrations/skinsrestorer.md)
- [Bridge protocol](docs/bridge-protocol.md) (for developers)

## Roadmap

1. **Foundation** - core, config, connection modes, world loading, integrations, lobby basics, bridge v1
2. **Placeholders and chat** - placeholder engine, full chat system (pipeline, anti-spam, filter, Persian/RTL support, channels) ← *done*
3. **Lobby experience** - NPCs and holograms (FancyNpcs/FancyHolograms-style commands), scoreboard, tab list,
   server selector, portals, multiple lobby instances, hotbar items, double jump, jump pads
4. **Fun, social and staff tools** - parkour, PvP area, cosmetics, daily rewards, vanish, in-game editors
5. **Production** - load testing (250-300 bots, mixed versions), Prometheus metrics, graceful shutdown, CI and Docker

## Known issues

- LuckPerms comes from a community Minestom port, built from a fork in `third_party/luckperms` that
  adds support for this Minestom version ([details](docs/integrations/luckperms.md)).
- Polar 1.16.0 was built for an older Minestom; the lobby includes a small compatibility fix
  (`LobbyPolarWorldAccess`) that is covered by tests.

## Building from source

Requirements: **JDK 25** and Git. The Gradle wrapper downloads everything else.

```bash
git clone --recursive https://github.com/mohammadhadimohammadi2007-dot/lobby.git
cd lobby
./gradlew build
```

Outputs:

- `lobby-server/build/libs/lobby-server.jar` - the server
- `lobby-bridge-velocity/build/libs/lobby-bridge.jar` - the Velocity plugin

Run a local test server (files go to `lobby-server/run/`):

```bash
./gradlew :lobby-server:run
```

Project layout:

```
lobby-server/           Minestom server
lobby-bridge-velocity/  Velocity plugin
lobby-common/           Shared code (bridge message format)
lobby-luckperms/        LuckPerms support (off until enabled in integrations.yml)
third_party/luckperms/  LuckPerms Minestom port (git submodule, built from source)
docs/                   Setup guides
```

## License

[MIT](LICENSE). Contributions are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).
