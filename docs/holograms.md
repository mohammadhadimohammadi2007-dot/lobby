# Holograms

Floating text, items and blocks. They are **packets only**: no entity exists on the server, so a
hologram costs no tick time, cannot be pushed or hit, and appears in every
[lobby instance](instances.md) of your map at once.

Everything is in `data/holograms.yml`, which `/hologram` writes for you.

## Quick start

```
/hologram create welcome
/hologram addline welcome <gold><bold>My Network
/hologram addline welcome <gray>Players online: <white>%server_online%
/hologram movehere welcome          brings it to where you stand
```

`/hologram create` puts the hologram where you are standing, with your own name as its first line.

## Commands

Permission: `lobby.command.hologram` (give it to staff only). Aliases: `/holograms`, `/hd`.

| Command | What it does |
|---|---|
| `/hologram list` | Every hologram |
| `/hologram near [radius]` | Holograms around you (16 blocks by default) |
| `/hologram info <name>` | Everything about one hologram |
| `/hologram create <name> [text\|item\|block] [item or block]` | Creates one where you stand |
| `/hologram delete <name>` | Deletes it |
| `/hologram copy <name> <new name>` | Copies it, including its lines and look |
| `/hologram movehere <name>` | Moves the hologram to you |
| `/hologram teleport <name>` | Moves **you** to the hologram |
| `/hologram addline <name> <text>` | Adds a line at the bottom |
| `/hologram setline <name> <number> <text>` | Replaces one line |
| `/hologram insertline <name> <number> <text>` | Puts a line before that line |
| `/hologram insertafter <name> <number> <text>` | Puts a line after that line |
| `/hologram removeline <name> <number>` | Removes one line |
| `/hologram set <name> <property> <value>` | Changes one property (below) |
| `/hologram action add <name> <action>` | Adds an action for clicks |
| `/hologram action list\|clear <name>` | Shows or removes the click actions |
| `/hologram show\|hide <name> <player>` | Shows or hides a hologram in `manual` visibility mode |
| `/hologram import` | Imports a FancyHolograms file (below) |

Line numbers start at 1. Names may use letters, digits, `-` and `_`, and are not case-sensitive.

### Coming from FancyHolograms

The subcommand names match FancyHolograms, including which way `teleport` and `movehere` go. Where this
lobby's name is different, FancyHolograms' name works as an alias:

| FancyHolograms | Here | |
|---|---|---|
| `remove <name>` | `delete <name>` | `remove` also works |
| `nearby <radius>` | `near <radius>` | `nearby` also works |
| `edit <name> <property> <value>` | `set <name> <property> <value>` | `edit` also works |
| `edit <name> addline <text>` | `addline <name> <text>` | the line commands are on their own here; typing it the FancyHolograms way tells you the command to use |
| `edit <name> insertbefore <n> <text>` | `insertline <name> <n> <text>` | `insertbefore` also works |
| `edit <name> visibilitydistance <n>` | `set <name> view-distance <n>` | `visibilitydistance` also works |
| `edit <name> updatetextinterval 5s` | `set <name> update-interval 100` | `updatetextinterval` also works and takes FancyHolograms' units: milliseconds, `5s`, `1m` or `never`. `update-interval` is in ticks |
| `edit <name> textshadow`, `seethrough`, `textalignment` | `text-shadow`, `see-through`, `alignment` | the FancyHolograms spellings also work |
| `edit <name> position` / `movehere` | `movehere <name>` | `position` and `here` also work |
| `edit <name> rotate`, `rotatepitch` | `set <name> rotate`, `rotate-pitch` | the FancyHolograms spellings also work |
| `edit <name> brightness block 7` | `set <name> brightness block 7` | same words |
| `edit <name> shadowradius`, `shadowstrength` | `set <name> shadow-radius`, `shadow-strength` | the FancyHolograms spellings also work |
| `edit <name> visibility <mode>` | `set <name> visibility <mode>` | `all`, `permission` and `manual`, as there |

Two things are deliberately different, because they cannot mean the same here:

- **`translate` and `center`** move a hologram by a part of a block relative to its own position.
  Nothing stops this from being added, but it would only duplicate `movehere` and a coordinate: this
  lobby has no block-centred grid to snap to, since holograms are placed where you stand, with
  decimals. Use `movehere` or edit `position` in the file.
- **`linkwithnpc`** attaches a hologram to a FancyNpcs NPC. The NPCs of this lobby carry their own
  name holograms, which follow them and are rendered with the same rules as any other hologram, so
  there is nothing to link: edit the name with `/npc name` (see [NPCs](npcs.md)). Importing a
  FancyHolograms hologram that is linked to an NPC makes it that NPC's name.

## Text

Lines are [MiniMessage](https://docs.advntr.dev/minimessage/format) and may use any
[placeholder](placeholders.md): `%server_online%`, `%player_name%`, `%luckperms_prefix%`,
`%lobby_online%`...

- A hologram **whose text contains a placeholder** is rebuilt once a second, and each player sees
  their own version. One without placeholders is built **once for the whole server**, however many
  players read it.
- Set `update-interval` yourself to change that: ticks, `0` for never, `-1` to decide from the text.
- Old clients (anything before 1.19.4, so 1.8 through ViaRewind as well) cannot show RGB colours, so
  those are mapped to the 16 colours they know.

## Properties

`/hologram set <name> <property> <value>`:

| Property | Values | Meaning |
|---|---|---|
| `scale` | 0.05 - 20 | How big it is |
| `billboard` | `center`, `vertical`, `horizontal`, `fixed` | How it turns towards the player |
| `alignment` | `center`, `left`, `right` | How lines of different lengths line up |
| `background` | `default`, `transparent`, `#RRGGBB`, `#AARRGGBB` | The box behind the text |
| `text-shadow` | `true`, `false` | Drop shadow behind the letters |
| `see-through` | `true`, `false` | Visible through blocks |
| `view-distance` | 1 - 128 blocks | How far away it is still shown |
| `update-interval` | ticks, `0`, `-1` | How often the text is rebuilt |
| `permission` | a permission node, or `""` | Who sees it at all |
| `line-spacing` | 0.05 - 2 blocks | Space between lines **on old clients** |
| `item` / `block` | an item or block name | What an item or block hologram shows |
| `type` | `text`, `item`, `block` | What kind it is |
| `rotate` | 0 - 360 degrees | Turns it, if `billboard` is `fixed` or `horizontal` |
| `rotate-pitch` | -90 - 90 degrees | Tips it, if `billboard` is `fixed` or `vertical` |
| `brightness` | `block <0-15>`, `sky <0-15>`, `default` | Lights it with a fixed light level instead of the light where it stands |
| `shadow-radius` | blocks, 0 for none | A round shadow on the ground under it |
| `shadow-strength` | 0 - 1 | How dark that shadow is |
| `visibility` | `all`, `permission`, `manual` | Who sees it (below) |

### Visibility

| Mode | Who sees it |
|---|---|
| `all` (the default) | everyone, unless `permission` is set as well |
| `permission` | players with the hologram's `permission`, or with `lobby.hologram.see.<name>` when it has none |
| `manual` | nobody, until `/hologram show <name> <player>`. The list is not saved, so it starts empty after a restart (as in FancyHolograms) |

Rotation, brightness and the shadow are drawn by the client's display entity, so clients older than
1.19.4 simply do not show them: a 1.8 player sees the same armor-stand text, upright and lit normally.

## Clicks

```
/hologram action add welcome open_menu: lobby
/hologram action add welcome sound: entity.experience_orb.pickup
```

Left and right clicks both run the list, once per click: holding the button down does not repeat it,
and the `click-cooldown` in the file (500 ms by default) is the shortest time between two runs. Every action the
lobby has works here, including `connect`, `lobby`, `open_menu`, `message`,
`title`, `sound`, `teleport`, `player_command`, `console_command` and `random`.

Because holograms are packets, this needs the raw click packet from the client: left clicks arrive as
an attack, right clicks as an interact, and only the main hand counts, so one click is one click.

## Item and block holograms

```
/hologram create shop item diamond_sword
/hologram create statue block diamond_block
/hologram set shop scale 2
```

On modern clients these are display entities. On old clients they become a floating item, because
display entities do not exist there; a block hologram then shows the block's **item** form.

## Animations

Several frames in `data/holograms.yml` are shown one after the other, every `update-interval` ticks:

```yaml
spinner:
  position: {x: 0.5, y: 66, z: 0.5}
  update-interval: 10
  lines: ["<gray>Loading ."]
  frames:
    - ["<gray>Loading ."]
    - ["<gray>Loading .."]
    - ["<gray>Loading ..."]
```

`lines` is always the first frame. Every viewer is on the same frame, because the frame comes from the
clock, not from a counter per player.

## The file

`data/holograms.yml`, written by the commands and safe to edit by hand (then `/lobby reload`).

```yaml
welcome:
  type: text                 # text, item or block
  position: {x: 0.5, y: 66.0, z: 0.5}
  lines:
    - "<gold><bold>My Network"
    - "<gray>Players: <white>%server_online%"
  scale: 1.0
  billboard: center
  alignment: center
  background: default        # default, transparent, #RRGGBB or #AARRGGBB
  text-shadow: false
  see-through: false
  view-distance: 48.0
  update-interval: -1        # -1 = decide from the text
  permission: ""             # "" = everyone
  line-spacing: 0.27         # only used on old clients
  click-cooldown: 500
  actions:
    - "message: <green>Welcome!"
```

A hologram that cannot be read is reported by name in the console, kept in the file untouched, and
skipped; every other hologram still loads.

## Importing from FancyHolograms

1. Copy FancyHolograms' `holograms.yml` into `<server folder>/import/holograms.yml`.
2. Run `/hologram import`.

The importer reads the keys FancyHolograms itself writes (checked against its source):

| FancyHolograms | Here |
|---|---|
| `location.x/y/z/yaw/pitch` | the position; the world is ignored, holograms belong to the lobby map |
| `type` | `text`, `item` or `block` |
| `text`, `text_alignment`, `text_shadow`, `see_through`, `background` | the same |
| `billboard`, `shadow_radius`, `shadow_strength`, `block_brightness`, `sky_brightness` | the same |
| `scale_x`, `scale_y`, `scale_z` | `scale`; holograms here scale evenly, so an uneven scale is made even and reported |
| `translation_x/y/z` | added to the position, which looks the same |
| `visibility_distance` | `view-distance` |
| `visibility`: `ALL`, `PERMISSION_REQUIRED`, `MANUAL` | `all`, `permission`, `manual`. The permission node changes from FancyHolograms' to `lobby.hologram.see.<name>` |
| `update_text_interval` | `update-interval`: FancyHolograms counts **milliseconds**, this lobby ticks, so 1000 becomes 20; `-1` (never) becomes 0 |
| `item` (both ways Bukkit writes an item) and `block` | the same item or block |
| `linkedNpc` | the hologram becomes **that NPC's name** instead of a hologram of its own. Import the NPCs first (`/npc import`), or the hologram is reported and skipped |

Anything that cannot be read is reported by name instead of guessed, and a hologram whose name already
exists is never overwritten. Check the result with `/hologram list` afterwards.

## What it costs

Every hologram is **classified** when it is loaded or edited, from the placeholders its lines use:

| The lines use | Rendered | Rebuilt |
|---|---|---|
| no placeholders, or only unknown ones | once for the whole server | never |
| only global placeholders (`%server_online%`, `%group_online_bedwars%`, `%bungee_total%`) | once for the whole server | every `update-interval` |
| a per-player placeholder (`%player_name%`, `%luckperms_prefix%`) | once per viewer **in range** | every `update-interval` |
| Persian or Arabic letters | twice per client generation: with the right-to-left fix and without, because that fix is a setting with two values | as above |

Measured on this machine (`HologramScaleTest`, 200 fake players standing at the hologram):

| | |
|---|---|
| `%group_online_bedwars% playing`, 200 viewers | **1** render per update, not 200 |
| `Hello %player_name%`, 20 viewers near and 50 far away | **20** renders per update, none for the 50 |
| no placeholders | 1 render, ever |
| `%server_online% بازیکن آنلاین`, 200 viewers in four groups (1.8 and modern, Persian fix on and off) | **4** renders per update |
| the same line in Latin letters, same 200 viewers | **2** renders (only the two client generations) |
| 50 holograms, 200 viewers | 50 renders; 97 ms for the first refresh including every spawn packet, 12 ms for a refresh with nothing changed |

### Which thread

Every part of this runs on one background thread named `lobby-display`: working out who can see what,
building the text, and sending the packets. The tick thread only counts ticks for it (one `+=` every
five ticks), so a hologram can never make MSPT worse, however many there are. A test asserts that the
text is only ever built on that thread.

| What | Measured | Thread |
|---|---|---|
| One player joining, 50 holograms, 200 players online | 11 ms more than an idle refresh (about 0.2 ms per hologram, for 100 packets) | `lobby-display` |
| `/lobby reload` with 50 holograms and 200 players | 65 ms (reads the file, despawns and shows everything again) | the reload's own thread and `lobby-display` |
| A refresh with nothing changed | 5 - 12 ms | `lobby-display` |

Those numbers come from the test harness, which serializes every packet into a list per fake player;
a real server writes them to a socket instead, so they are an upper bound.

| | |
|---|---|
| Server tick | Nothing: no entity, no tick handler. The text is built on a background thread |
| Packets | One spawn plus one metadata packet per viewer group, then only what changed; a viewer whose text did not change gets nothing |
| Viewer groups | Players who would see exactly the same thing share one render and one set of entity ids |

## Limits

- At most 32 lines per hologram, 50 holograms is nothing to worry about (they cost almost nothing when
  nobody is near them).
- A hologram belongs to the lobby map, so it appears in every lobby instance. It does **not** appear in
  other worlds the server may run later.
- Old clients see one armor stand per line. Lines are spaced by `line-spacing` there, while modern
  clients place the lines themselves, so the two can look slightly different.
