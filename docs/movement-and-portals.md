# Movement and portals

Double jump, `/fly`, jump pads and launch pads are set in `movement.yml` and apply with `/lobby reload`.
Portals are made in game with `/portal` and stored in `data/portals.yml`.

Speeds are in blocks per second. Sounds are Minecraft sound names (`entity.bat.takeoff`), particles
particle names (`cloud`); `""` means none. An unknown name is reported in the console and left out.

## Double jump

```yaml
double-jump:
  enabled: true
  permission: ""          # "" = everyone
  cooldown: 1000          # milliseconds; players must also land in between
  forward: 18             # speed in the look direction
  up: 12
  sound: "entity.bat.takeoff"
  particle: "cloud"
```

Press jump again in the air: you are thrown forward and up. Players in creative or spectator mode, and
players with `/fly` on, fly normally instead.

## /fly

`/fly` turns flying on and off. The permission is `fly.permission` in `movement.yml` (default
`lobby.fly`). Double jump is off while flying.

## Jump pads

A pressure plate on top of a certain block throws players in the direction they look:

```yaml
jump-pads:
  enabled: true
  pads:
    - plate: "light_weighted_pressure_plate"
      below: "slime_block"      # or "any"
      forward: 30
      up: 16
      sound: "entity.firework_rocket.launch"
      particle: "firework"
```

The first matching pad wins. A pad throws the same player at most once every half second.

## Launch pads

A box of blocks that throws everyone who walks in with the same velocity, wherever they look. Good for
a ramp up to a tower:

```yaml
launch-pads:
  - name: tower
    from: [10, 64, 10]          # corners, in any order
    to: [12, 64, 12]
    velocity: [0, 30, -20]      # x, y, z
    sound: "entity.firework_rocket.launch"
    particle: "firework"
```

## Portals

A portal is a box of blocks that runs actions for every player who walks in, with the same actions as
NPCs, menus and hotbar items. It exists in every lobby instance.

| Command | What it does |
|---|---|
| `/portal wand` | Gives the wand: hit a block for corner 1, right-click one for corner 2 |
| `/portal pos1`, `/portal pos2` | Sets a corner where you stand |
| `/portal create <name>` | Makes a portal from the two corners (at most 50 000 blocks) |
| `/portal action <name> add <action>` | Adds an action, e.g. `connect_group: bedwars` |
| `/portal action <name> remove <n>` / `clear` / `list` | Edits or shows its actions |
| `/portal cooldown <name> <time>` | `2s`, `500ms` or `off`: the shortest time between two uses by one player (2 s by default) |
| `/portal permission <name> <node>` | Only players with it may use it; `@none` for everyone |
| `/portal info <name>`, `/portal list` | Shows portals |
| `/portal teleport <name>` | Takes you there |
| `/portal remove <name>` | Removes it |

Permission: `lobby.command.portal`.

**Pushed back out.** If a `connect` or `connect_group` action of the portal cannot send the player
away (the server is full, offline or unknown, or there is no proxy), the player is put back where they
stepped in and pushed away from the portal. They are told why, and they are never stuck inside. The same
happens to a player without the portal's permission.

Behind BungeeCord the lobby never hears whether a connect worked, so there players are not pushed back.

`data/portals.yml` can also be edited by hand (then `/lobby reload`):

```yaml
bedwars:
  from: [10, 64, 10]
  to: [12, 67, 10]
  cooldown: 2000
  permission: ""
  actions:
    - "connect_group: bedwars"
```

A portal that cannot be read (missing corners, too big) is reported by name, kept in the file
untouched, and skipped.
