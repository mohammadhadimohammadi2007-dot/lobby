# NPCs

Players and mobs that stand in the lobby and do something when clicked: send players to a game, open a
menu, greet them. Like [holograms](holograms.md) they are **packets only**: no entity exists on the
server, so an NPC costs no tick time, cannot be pushed or hurt, and appears in every
[lobby instance](instances.md) of the map at once.

Everything is in `data/npcs.yml`, which `/npc` writes for you.

## Quick start

```
/npc create bedwars
/npc skin bedwars Notch
/npc displayname bedwars <gold><bold>BedWars
/npc name bedwars addline <gray>%group_online_bedwars% playing
/npc name bedwars addline <yellow>Click to join
/npc action bedwars any_click add connect_group: bedwars
/npc turn_to_player bedwars true
```

`/npc create` puts the NPC where you are standing, facing the way you face.

## Commands

Permission: `lobby.command.npc` (staff only). Alias: `/npcs`. The subcommands have FancyNpcs' names,
so admins coming from FancyNpcs can type what they know.

| Command | What it does |
|---|---|
| `/npc create <name>` | Creates a player NPC where you stand |
| `/npc remove <npc>` | Removes it (`delete` also works) |
| `/npc copy <npc> <new name>` | Copies it, with everything it has |
| `/npc list` | Every NPC |
| `/npc nearby [radius]` | NPCs around you (16 blocks by default) |
| `/npc info <npc>` | Everything about one NPC |
| `/npc teleport <npc>` | Moves **you** to the NPC |
| `/npc move_here <npc>` | Moves the NPC to you, facing your way |
| `/npc move_to <npc> <x> <y> <z> [yaw] [pitch]` | Moves the NPC to a position |
| `/npc type <npc> <type>` | `player`, `villager`, `zombie`... any entity type |
| `/npc skin <npc> <skin>` | See [Skins](#skins) |
| `/npc displayname <npc> <text>` | A one-line name; `@none` hides it |
| `/npc name <npc> ...` | The name as a full hologram: see [The name](#the-name) |
| `/npc equipment <npc> set <slot> <item>` | `main_hand`, `off_hand`, `helmet`, `chestplate`, `leggings`, `boots`; `air` empties a slot |
| `/npc equipment <npc> list\|clear` | Shows or removes everything it holds and wears |
| `/npc glowing <npc> [true\|false\|toggle]` | A glowing outline |
| `/npc turn_to_player <npc> [true\|false\|toggle]` | Looks at players who come near |
| `/npc turn_to_player_distance <npc> <blocks>` | How near "near" is (5 by default) |
| `/npc visibility_distance <npc> <blocks>` | How far away it is still shown (48 by default) |
| `/npc interaction_cooldown <npc> <time>` | `500ms`, `2s` or `disabled`: the shortest time between two clicks that do something |
| `/npc permission <npc> <node>` | Only players with this permission see it; `@none` for everyone |
| `/npc action <npc> <trigger> ...` | See [Clicks](#clicks) |
| `/npc import` | Imports a FancyNpcs file, see [Importing](#importing-from-fancynpcs) |

## Skins

| `/npc skin <npc> ...` | Skin |
|---|---|
| `Notch` | The skin of that premium account, looked up at Mojang |
| `@mirror` | Every player sees the NPC wearing **their own** skin |
| `@none` | Minecraft's default skin |
| `sr:knight` | A custom skin saved in SkinsRestorer (needs the SkinsRestorer integration) |
| `mineskin:<uuid>` or a `https://mineskin.org/...` link | A skin already uploaded to MineSkin |

Skins are looked up in the background and saved in `data/npcs.yml` (`skin-texture`), so a restart never
waits for Mojang or MineSkin, and an NPC keeps its skin even when they are down. `/npc skin` again looks
it up again.

A **link to an image** does not work: turning an image into a skin means uploading it to MineSkin, which
needs a MineSkin API key. Upload it on mineskin.org yourself and use the link of the skin it gives you;
reading an existing MineSkin skin needs no key.

Only player NPCs have skins. `/npc type <npc> player` makes one a player again.

## The name

The name above an NPC is a full [hologram](holograms.md): several lines, placeholders, and the same look
settings. It replaces FancyNpcs' one-line display name and FancyHolograms' `linkwithnpc` in one, and it
always follows the NPC.

```
/npc name <npc> addline <text>
/npc name <npc> setline <number> <text>
/npc name <npc> insertline <number> <text>      (insertbefore also works)
/npc name <npc> insertafter <number> <text>
/npc name <npc> removeline <number>
/npc name <npc> set <property> <value>
```

The properties are the hologram ones that make sense for a name: `scale`, `billboard`, `alignment`,
`background`, `text-shadow`, `see-through`, `view-distance`, `update-interval`, `line-spacing`,
`brightness`, `shadow-radius` and `shadow-strength` (FancyHolograms' spellings also work).

The name is rendered with exactly the same rules as a hologram, measured with 200 players
(`NpcEnvTest`):

| Name | Built |
|---|---|
| `BedWars` / `%group_online_bedwars% playing` / `Click to join` | **once** per update for all 200 players |
| `Hello %player_name%` | once per player in range (200) |
| Persian or Arabic text | once with the right-to-left fix and once without |

## Clicks

Each NPC has three lists of [actions](holograms.md#clicks), one per trigger, as in FancyNpcs:

| Trigger | Runs on |
|---|---|
| `any_click` | either button |
| `left_click` | a left click (hit) |
| `right_click` | a right click (use) |

A left click runs `any_click` and `left_click`. A click on the name counts as a click on the NPC.

```
/npc action <npc> <trigger> add <action>
/npc action <npc> <trigger> add_before|add_after|set <number> <action>
/npc action <npc> <trigger> remove|move_up|move_down <number>
/npc action <npc> <trigger> clear|list
```

An action can be written this lobby's way (`message: Hello`, with a colon) or FancyNpcs' way
(`message Hello`, without one). FancyNpcs' action types are translated:

| FancyNpcs | Here |
|---|---|
| `message`, `player_command`, `console_command`, `need_permission` | the same |
| `send_to_server <server>` | `connect: <server>` |
| `play_sound <sound>` | `sound: <sound>` |
| `wait <seconds>` | `wait: <ticks>`: FancyNpcs counts **seconds**, this lobby ticks, so `wait 2` becomes `wait: 40` |
| `execute_random_action` | a `random:` section holding every action after it, since FancyNpcs runs one of those and stops |
| `block_until_done` | not needed: the click cooldown already stops a list from running twice at once |
| `player_command_as_op` | refused: it makes the player an operator for a moment, which a lobby must never do. Use `console_command` |

Clicks work through the raw click packets (Minestom has no entity to fire an event for): only the main
hand counts, so one click is one click, and clicks are rate-limited per player on top of the cooldown.

## The file

`data/npcs.yml`, written by the commands and safe to edit by hand (then `/lobby reload`):

```yaml
bedwars:
  type: player                 # any entity type
  position: {x: 0.5, y: 65.0, z: 3.5, yaw: 180.0, pitch: 0.0}
  skin: Notch                  # @none, @mirror, a player name, sr:<name>, mineskin:<uuid>
  skin-texture:                # the looked-up skin, kept so a restart needs no lookup
    value: "..."
    signature: "..."
  turn-to-player: true
  turn-distance: 5.0
  glowing: false
  equipment:
    main_hand: red_bed
  view-distance: 48.0
  permission: ""               # "" = everyone
  name:                        # a hologram: lines, frames, scale, background...
    lines:
      - "<gold><bold>BedWars"
      - "<gray>%group_online_bedwars% playing"
      - "<yellow>Click to join"
    background: transparent
  click-cooldown: 500
  actions:                     # any_click
    - "connect_group: bedwars"
  right-click-actions:
    - "open_menu: bedwars"
```

An NPC that cannot be read (no position, an unknown type) is reported by name in the console, kept in the
file untouched, and skipped; every other NPC still loads.

## Importing from FancyNpcs

1. Copy FancyNpcs' `npcs.yml` into `<server folder>/import/npcs.yml`.
2. Run `/npc import`.
3. If you use FancyHolograms holograms linked to those NPCs, run `/hologram import` afterwards: each
   linked hologram becomes its NPC's name.

The importer reads the keys FancyNpcs itself writes (checked against its source): name, display name,
type, location, skin (a looked-up name or link, a saved texture, or mirror), glowing, turn to player and
its distance, interaction cooldown (seconds there, milliseconds here), visibility distance, equipment
(both ways Bukkit writes an item) and the actions of every trigger, translated as above.

Not imported, and reported by name when used: an NPC's `scale` (NPCs here are their normal size), the
glowing colour, `collidable` (packet NPCs never collide) and `show_in_tab` (an NPC is never in the tab
list here). An NPC whose name already exists is never overwritten.

## Things to know

- A player NPC is a player only the clients know about. It is added to their player list, unlisted, so
  it never shows in the tab list, and its name above the head is hidden by the lobby's hidden-name team;
  the real name is the name hologram.
- Turning towards players is sent to each player separately and only when the direction changed by more
  than 2 degrees, so a still player costs nothing.
- Clients older than 1.19.4 (1.8 through ViaRewind) see the same player or mob, and its name as
  armor-stand lines, like any hologram. Whether ViaRewind keeps an unlisted NPC out of a 1.8 tab list is on the manual test
  checklist, because it depends on ViaRewind, not on this lobby.
