# LiteBans

Reads bans and mutes straight from the [LiteBans](https://www.spigotmc.org/resources/litebans.3715/)
tables. **Read only**: the lobby never creates, changes or removes punishments.
LiteBans itself does not need to be installed on the lobby.

```yaml
litebans:
  enabled: true
  table-prefix: "litebans_"
  server-name: "lobby"
  check-interval: 3
```

## What it does

| Mode | Bans | Mutes |
|---|---|---|
| `standalone` | Banned players are refused at login with reason, staff name and expiry (from `messages.yml`) | Known to the lobby |
| `velocity` / `bungeecord` | Not checked (LiteBans on your proxy already does it) | Known to the lobby |

Mutes: when a player joins, their active mute is loaded. Every `check-interval` seconds the lobby reads
only the **new** mute rows and re-checks the mutes it has cached, so new mutes and unmutes apply within
a few seconds. The chat system (Phase 2) uses this to block muted players.

Handled: temporary and permanent punishments, IP bans and IP mutes, and server scopes. A punishment
counts if it is global (`*`) or scoped to `server-name`.

## Tables read

- `<prefix>bans` and `<prefix>mutes`: columns `id`, `uuid`, `ip`, `reason`, `banned_by_name`, `until`,
  `ipban`, `active`, `server_scope`.

A punishment is active when `active = 1` and `until` is 0/negative (permanent) or in the future, the same
rule LiteBans' web interface uses.

## Requirements on the LiteBans side

- LiteBans must use **MySQL/MariaDB** storage (not H2), pointing at the same database as `integrations.yml`.
- `table-prefix` must match `table_prefix` in LiteBans' `config.yml`.

If the tables are not found, the console says so at startup and LiteBans is marked `FAILED`.
If the database is unreachable during a login, the player is let in and the error is logged.
