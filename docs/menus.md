# Menus

Menus are chest windows, set in `menus.yml` and opened with the action `open_menu: <name>`: from a hotbar
item, an NPC, a hologram, a portal or a command. They apply with `/setup-lobby reload`.

Menus are read-only. Every click is cancelled before anything moves, so players can never take, drop,
shift-click or swap the items. Only the clicked item's actions run.

## A menu

```yaml
menus:
  servers:
    title: "<dark_gray>Games"     # MiniMessage, %placeholders% work
    rows: 3                       # 1-6
    refresh: 20                   # ticks between rebuilds while open (0 = never)
    fill: "black_stained_glass_pane"
    items:
      skywars:
        slot: 13                  # 0 is the top left, 8 the top right; or slots: [0, 8]
        material: "ender_eye"
        amount: 1
        name: "<aqua><bold>SkyWars"
        lore:
          - "<gray>Playing: <white>%group_online_skywars%"
        glow: false
        show-if:
          - "%group_status_skywars% == online"
        actions:
          - "close_menu"
          - "connect_group: skywars"
        click-cooldown: 250       # milliseconds
```

## Sending players to servers, and proxy commands

The same actions as everywhere else ([NPCs](npcs.md#sending-players-to-servers-and-proxy-commands)):

| Action | What it does |
|---|---|
| `connect: <server>` | Sends the player to that server (its name in `velocity.toml`) |
| `connect_group: <group>` | Sends the player to the best server of a group from the bridge's `config.toml` (Velocity with the bridge) |
| `player_command: <command>` | Runs the command as the player. A lobby command (`spawn`, `lobbies`...) runs on the lobby; **any other command is sent to the proxy and runs there as the player**, for example `player_command: server bedwars` or a queue plugin's `player_command: queue bedwars` |
| `proxy_command: <command>` | Always runs on the proxy as the player, for a command both have (such as the proxy's `/hub` or `/lobby`) |

- Commands on the proxy run with **the player's own permissions there**: a player can only run what they
  could type themselves. If the proxy has no such command, or the player may not use it, they are told
  (`proxy-command-unknown` in messages.yml) and the console names the command, which also shows typos.
- This needs Velocity with lobby-bridge 0.5 or newer, and `player-commands = true` in the bridge's
  `config.toml` (the default). In standalone mode, or behind BungeeCord, the player is told that the
  command is not available without a proxy (`proxy-command-not-available`), and the console says why once.
- Placeholders work: `player_command: party join %player_name%`.
- Only actions set up by admins do this. What a player types is never passed on by the lobby.
- For switching servers, `connect` and `connect_group` are better than `player_command: server ...`: they
  say why it did not work (full, offline) and push a player back out of a portal.

## One slot, different items: `show-if`

Several entries may use the same slot. The first one whose `show-if` conditions all hold is shown, so a
slot can look different per player or per server status:

- `permission: lobby.vip`: the viewer has the permission.
- A comparison with placeholders: `%server_online% > 10`, `%group_status_bedwars% == full`. The operators
  are `==`, `!=`, `>`, `<`, `>=`, `<=` and `contains`. Numbers are compared as numbers; text ignores
  capitals.

An entry without `show-if` always holds, so put it last as the fallback.

## The server selector

The bundled `servers` menu is the game selector. The compass in the hotbar opens it. Each game has three
entries in one slot, chosen by `%group_status_<group>%`:

| Status | When | Bundled item |
|---|---|---|
| `online` | a server of the group is up and has room | the game's item, "Click to play" (`connect_group`) |
| `full` | every server that is up is full | the game's item, "Full right now", nothing to click |
| `offline` | no server of the group is up | a barrier, "Offline" |

`%group_online_<group>%` and `%group_max_<group>%` give the player count and the total limit. The groups,
counts and statuses come from the proxy bridge (see [Velocity](velocity.md)). Without the bridge every
group is `offline`. The menu refreshes every second while open.

Replace `bedwars`, `skywars` and `duels` with your own groups.

## The lobby selector

`open_menu: lobbies` (or `/lobbies`) opens a menu the lobby builds itself, so it needs no entry in
`menus.yml`:

- one paper item per lobby instance of this server (see [instances](instances.md)), its stack size the
  lobby number. Your own lobby glows. A click moves you there;
- from the next row on, every other lobby server in the bridge group `lobbies`, with its player count. A
  click connects you there. An offline server is shown as a barrier and cannot be clicked.

Its texts are the `lobby-selector-*` lines of `messages.yml`. A menu called `lobbies` in `menus.yml`
replaces it completely.

## Items on 1.8

An item that does not exist in Minecraft 1.8 (such as `red_bed`) is reported once in the console with
the option it is used in. ViaRewind shows those players a replacement, and it still works for everyone
else. The bundled menus only use items that exist on 1.8.
