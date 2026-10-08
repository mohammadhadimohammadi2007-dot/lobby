# LuckPerms

LuckPerms gives the lobby the same ranks, permissions, prefixes and suffixes as the rest of your
network. The lobby runs its own LuckPerms instance against your network's MariaDB, so it reads the
same `luckperms_` tables as LuckPerms on your proxy and Paper servers.

```yaml
luckperms:
  enabled: true
  server-name: "lobby"
  table-prefix: "luckperms_"
  messaging-service: "sql"
```

- All LuckPerms settings come from `integrations.yml` and the shared `database:` block. No separate
  LuckPerms config file is needed. LuckPerms keeps its own small connection pool.
- `table-prefix` must match `table-prefix` in your other LuckPerms configs.
- `messaging-service` must match the other LuckPerms configs on your network. With `sql`, a rank change
  made anywhere (for example `/lp user Steve parent add vip` on the proxy) reaches the lobby within a
  couple of seconds, even when no player is on the lobby.
- `server-name` is this server's name for server-specific permissions (`/lp ... server=lobby`).
- Give your default group `lobby.command.spawn` so players can use `/spawn`.
- The **primary group** (used to pick chat formats) is LuckPerms' stored primary group, as on your other
  servers: `/lp user Steve parent set vip` changes it, `parent add` does not.

If LuckPerms is enabled but cannot start (for example the database is unreachable), the console shows
why, LuckPerms is marked `FAILED`, and the lobby falls back to the `operators:` list in `config.yml`.

## What the lobby uses

| Data | Used for |
|---|---|
| Permissions | Every permission check (`lobby.command.*`, `lobby.bypass.*`, chat permissions...) |
| Prefix / suffix / primary group | Chat formats, tab list and scoreboard (Phase 2+) |
| Meta (`/lp group vip meta set key value`) | Placeholders like `%luckperms_meta_key%` |

When LuckPerms rebuilds a player's data (`UserDataRecalculateEvent`), the lobby refreshes that player's
command list and drops cached rank values, so changes show up right away.

## How it is built

LuckPerms has no official Minestom version. The lobby uses the community port at
[LooFifteen/LuckPerms](https://github.com/LooFifteen/LuckPerms/tree/feat/minestom), through the fork
[mohammadhadimohammadi2007-dot/LuckPerms](https://github.com/mohammadhadimohammadi2007-dot/LuckPerms/tree/feat/minestom)
(branch `feat/minestom`), which adds support for Minestom `2026.10.05-26.2` and Adventure 5.

The fork is a git submodule in `third_party/luckperms` and is compiled together with the lobby
(a Gradle composite build), so no Maven repository is needed. Clone with submodules:

```bash
git clone --recursive https://github.com/mohammadhadimohammadi2007-dot/lobby.git
# or, in an existing clone:
git submodule update --init
```

## Changes in the fork

Compared to the upstream port, the fork:

- builds against Minestom `2026.10.05-26.2` and adds `slf4j-api` as `compileOnly`
  (Minestom no longer exposes it);
- moves `common` to Adventure `5.2.0`: `TranslationRegistry` → `TranslationStore.messageFormat(...)`,
  `UTF8ResourceBundleControl` → plain `ResourceBundle.getBundle(...)` (UTF-8 since Java 9),
  `TranslatableComponent.Builder#args` → `#arguments`;
- updates the tests for Adventure 5 (`Component` is sealed and cannot be mocked).

All 1116 LuckPerms `common` tests pass. These changes are meant to go upstream as a pull request.

## Tested

`LuckPermsLiveIT` (in `lobby-luckperms`) runs against a real MariaDB: it starts LuckPerms in the lobby,
then a second, independent LuckPerms instance in another JVM gives the player a new group with a prefix
and a permission on the same tables, and checks that the lobby picks the change up through SQL messaging.
It runs when `LOBBY_TEST_DB_PORT` is set (see [CONTRIBUTING.md](../../CONTRIBUTING.md)).
