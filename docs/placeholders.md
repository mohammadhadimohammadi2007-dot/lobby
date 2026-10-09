# Placeholders

Placeholders are words between `%` signs that the lobby replaces with live values, for example
`%player_name%` or `%bungee_total%`. They use the same syntax and, where possible, the same names as
[PlaceholderAPI](https://wiki.placeholderapi.com/) on Paper, so texts copied from your other servers keep working.

They work in `messages.yml`, the MOTD in `config.yml`, chat formats, join announcements and broadcasts,
and (from Phase 3 on) scoreboards and holograms.

```yaml
spawn-teleported: "<prefix><gray>Welcome back, %luckperms_prefix%%player_name%!"
```

- **Syntax:** `%namespace_key%` or `%namespace_key_argument%`.
- **Unknown placeholders** are left exactly as written (like PlaceholderAPI). Turn on debug logging to see
  which ones were not recognized.
- **Colors in values:** values from trusted sources (LuckPerms prefixes and meta) may contain colors in
  MiniMessage (`<gold>`) or legacy form (`&6`, `§6`, `&#ffaa00`). Values players can influence (names) are
  always shown as plain text, so nobody can inject colors, click actions or hover text through them.
- **Inside tags:** a placeholder inside `<hover:show_text:'...'>` keeps its colors. Inside other tag arguments,
  such as `<click:run_command:'/msg %player_name%'>`, it is inserted as plain text with `' " < > \ :` removed.

## Built-in placeholders

### Player (`player_`)

| Placeholder | Value |
|---|---|
| `%player_name%` | Username |
| `%player_displayname%` | Display name (plain text) |
| `%player_uuid%` | UUID |
| `%player_ping%` | Ping in milliseconds |
| `%player_world%` | This lobby's name (`server.name`) |
| `%player_protocol_version%` | Real client protocol number (from the bridge), e.g. `47` |
| `%player_client_version%` | Real client version, e.g. `1.8.x`, `1.21.4`, `26.2` |

Without the bridge, the client version is the lobby's own version.

### Ranks (`luckperms_`)

Same names as the LuckPerms expansion. Work with LuckPerms and with the `operators:` list (which has no
prefixes, so those are empty).

| Placeholder | Value |
|---|---|
| `%luckperms_prefix%` | Prefix (with colors) |
| `%luckperms_suffix%` | Suffix (with colors) |
| `%luckperms_primary_group_name%` | Primary group, e.g. `vip` |
| `%luckperms_meta_<key>%` | A meta value, e.g. `%luckperms_meta_chat_color%` |
| `%luckperms_has_permission_<permission>%` | `yes` or `no`, e.g. `%luckperms_has_permission_lobby.vip%` |

These are cached per player and refreshed as soon as LuckPerms reports a change.

### This server (`server_`)

| Placeholder | Value |
|---|---|
| `%server_online%` | Players on this lobby |
| `%server_max_players%` | `server.max-players` |
| `%server_name%` | `server.name` |
| `%server_tps%` | Ticks per second, e.g. `20.0` |
| `%server_mspt%` | Milliseconds per tick, e.g. `0.42` |

### Network (needs the [bridge](velocity.md#3-install-the-bridge-optional-recommended))

| Placeholder | Value |
|---|---|
| `%bungee_total%` | Players on the whole network (same name as PlaceholderAPI's Bungee expansion) |
| `%bungee_<server>%` | Players on one backend server, e.g. `%bungee_bw-1%` |
| `%group_online_<group>%` | Players on all servers of a bridge group, e.g. `%group_online_bedwars%` |
| `%group_max_<group>%` | The player limits of the group's servers that are up, added together |
| `%group_status_<group>%` | `online` (a server is up and has room), `full` (every server that is up is full) or `offline`. For menus that change with the status ([menus](menus.md)) |
| `%lobby_servers%` | Number of lobby servers on the network (size of the `lobbies` bridge group, or 1) |

Without the bridge, all counts are `0`.

### Lobby instances ([guide](instances.md))

These work without the bridge: they are about the lobbies of **this** server.

| Placeholder | Value |
|---|---|
| `%lobby_id%` | Which lobby instance the player is in (`1` on a normal server) |
| `%lobby_count%` | How many lobby instances this server has |
| `%lobby_online%` | Players in the player's own lobby instance |
| `%lobby_online_<number>%` | Players in that lobby instance, e.g. `%lobby_online_2%` |
| `%lobby_name%` | This server's name (`server.name`) |

### Mutes (`litebans_`, needs the [LiteBans integration](integrations/litebans.md))

| Placeholder | Value |
|---|---|
| `%litebans_muted%` | `yes` or `no` |
| `%litebans_mute_reason%` | Reason of the active mute, or empty |
| `%litebans_mute_expires%` | Expiry date, `ban-never-expires` from messages.yml for permanent mutes, or empty |

## Adding your own (for developers)

```java
PlaceholderRegistry registry = lobbyServer.text().placeholders().registry();

registry.register("coins", params -> switch (params) {
    // %coins_balance%: one value per player, cached until invalidated
    case "balance" -> Placeholder.player(player -> String.valueOf(bank.balance(player)));
    // %coins_top%: same for everyone, recomputed at most every 10 seconds
    case "top" -> Placeholder.global(Duration.ofSeconds(10), () -> bank.topName());
    default -> null; // unknown: left as written
});
```

- `Placeholder.global(cacheTime, supplier)`: same value for everyone, cached for `cacheTime`.
- `Placeholder.player(function)` / `Placeholder.player(cacheTime, function)`: per player; cached until
  `PlaceholderService.invalidate(uuid)` (or for `cacheTime`). Cleared automatically when the player leaves.
- `Placeholder.formattedPlayer(function)`: like `player`, but the value may contain colors. Only for trusted values.
- `new Placeholder.Relational((viewer, target) -> ...)`: depends on who reads and who it is about. Never cached.

`lookup(params)` is called once per distinct placeholder text, so parse arguments there and return a
`Placeholder` that does the minimum work per call.
