# Menus

Menus are chest windows, set in `menus.yml` and opened with the action `open_menu: <name>`: from a hotbar
item, an NPC, a hologram, a portal or a command. They apply with `/lobby reload`.

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
