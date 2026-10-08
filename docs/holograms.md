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
/hologram teleport welcome          brings it to where you stand
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
| `/hologram teleport <name>` | Moves the hologram to you |
| `/hologram goto <name>` | Moves you to the hologram |
| `/hologram addline <name> <text>` | Adds a line at the bottom |
| `/hologram setline <name> <number> <text>` | Replaces one line |
| `/hologram insertline <name> <number> <text>` | Puts a line in the middle |
| `/hologram removeline <name> <number>` | Removes one line |
| `/hologram set <name> <property> <value>` | Changes one property (below) |
| `/hologram action add <name> <action>` | Adds an action for clicks |
| `/hologram action list\|clear <name>` | Shows or removes the click actions |
| `/hologram import` | Imports a FancyHolograms file (below) |

Line numbers start at 1. Names may use letters, digits, `-` and `_`, and are not case-sensitive.

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

What both have in common is converted: position, type, text, alignment, text shadow, billboard, scale,
visibility distance, background and the text update interval (FancyHolograms counts it in seconds, this
lobby in ticks). The world in the file is ignored, because holograms here belong to the lobby map.
Anything that cannot be read is reported by name instead of guessed, and a hologram whose name already
exists is never overwritten. Check the result with `/hologram list` afterwards.

## What it costs

| | |
|---|---|
| Server tick | Nothing: no entity, no tick handler. The text is built on a background thread |
| Packets | One spawn plus one metadata packet per viewer group, then only what changed |
| Viewer groups | Players who would see exactly the same thing share one render and one set of entity ids |
| Text without placeholders | Built once for the whole server |

## Limits

- At most 32 lines per hologram, 50 holograms is nothing to worry about (they cost almost nothing when
  nobody is near them).
- A hologram belongs to the lobby map, so it appears in every lobby instance. It does **not** appear in
  other worlds the server may run later.
- Old clients see one armor stand per line. Lines are spaced by `line-spacing` there, while modern
  clients place the lines themselves, so the two can look slightly different.
