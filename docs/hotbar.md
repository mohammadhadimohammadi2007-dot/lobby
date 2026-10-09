# Hotbar items and player visibility

`hotbar.yml` sets the items players get in their hotbar. They are given when a player joins and every
time they change lobby instance. Changes apply with `/lobby reload`, which gives everyone their items
again.

## Items

```yaml
enabled: true
clear-inventory: true            # empty the inventory before giving the items
items:
  games:
    slot: 0                      # 0 (left) to 8 (right)
    material: compass
    name: "<green><bold>Game Selector</bold> <gray>(right-click)"
    lore:
      - "<gray>Pick a game to play."
    glow: false
    permission: ""               # only players with it get the item; "" = everyone
    actions:                     # what right-clicking with it does
      - "open_menu: servers"
    click-cooldown: 250          # milliseconds
```

Names and lore are MiniMessage with placeholders. The actions are the same as everywhere else
(`open_menu`, `connect_group`, `lobby`, `player_command`, `teleport`, `sound`...). Two items cannot share a
slot; the second one is reported and left out.

The bundled hotbar has the game selector (compass, `servers` menu), the lobby selector (nether star,
`lobbies` menu), the visibility switch and the lobby menu (chest). See [menus](menus.md).

### Locked

Players can never move, drop, swap to the off hand or place a hotbar item. Using one never eats, throws
or places it either. Each item carries a hidden tag with its name, so it is recognised wherever it is.

Only right-click runs actions. A left-click cannot be told apart reliably, because clients also swing
their arm on a right-click.

## Player visibility

An item with `type: visibility` is the switch for who you see. Each click moves to the next mode, and
the item shows the current one:

| Mode | You see |
|---|---|
| `all` | every player |
| `staff` | only players with `visibility.staff-permission` |
| `none` | nobody |

```yaml
items:
  visibility:
    slot: 7
    type: visibility
    modes:                       # one look per mode
      all:   {material: lime_dye,   name: "<green>Players: <white>all shown"}
      staff: {material: purple_dye, name: "<light_purple>Players: <white>staff only"}
      none:  {material: gray_dye,   name: "<gray>Players: <white>hidden"}
visibility:
  default: all                   # before a player ever uses the switch
  staff-permission: "lobby.visibility.staff"
  cooldown: 3000                 # milliseconds between two switches
```

- **NPCs and holograms are never hidden.** Only players are.
- **The choice is saved** with the player's other settings, in the database if the chat storage uses
  one, otherwise in `data/chat-settings/`. It comes back on the next join, on every lobby server that
  shares the database.
- **Hidden players are simply not sent** to the viewer, so they cost that viewer nothing. They still
  show in the tab list.
- The messages are the `visibility-*` lines of `messages.yml`.
