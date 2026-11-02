# Cosmetics

Trails, hats, outfits, pets, balloons and gadgets players choose in the cosmetics menu. A player uses one of each
kind at a time, and their choice is saved (in the shared database if the lobby has one, so it follows them to every
lobby).

| Kind | What it is |
|---|---|
| Trail | Particles that follow the player: at the feet while walking, a halo, a spiral, a ring on the ground, wings, or a rain cloud |
| Hat | An item or block on the head, or a player head with any skin |
| Outfit | Armor worn as clothes: dyed leather, gold, diamond... |
| Pet | A small animal walking after the player |
| Balloon | A block or head floating above the player on a lead |
| Gadget | An item in the hotbar that does something when right-clicked: a firework, a leap, a burst of particles, a paintball, a grappling hook, confetti, a fake TNT or lightning. Each has a cooldown |

Everything is in `cosmetics.yml` (created on the first start, every option commented). Changes apply with
`/setup-lobby reload`.

## It works out of the box

A fresh lobby has 12 trails, 10 hats, 6 outfits, 10 pets, 6 balloons and 8 gadgets, some free for everyone and
some locked (to show how locking looks). Players open the menu with:

- the **Cosmetics** item in the hotbar (slot 7, an ender chest),
- `/cosmetics`,
- or `open_menu: cosmetics` on anything with [actions](npcs.md#clicks) (an NPC, a menu item, a portal).

The gadget they pick shows in the hotbar's middle slot (slot 5).

**Check it worked:** right-click the ender chest, open Trails, click "Flames" and walk: flames follow you. Click it
again to take it off.

> An existing lobby's `hotbar.yml` is not changed by an update. Add the two items yourself (see
> [Hotbar](#the-hotbar) below), or delete `hotbar.yml` to get the new default.

## The menu

The first page has the six kinds (with how many the player has unlocked and what they use), a switch for
**other players' cosmetics** (shown or hidden), and **take everything off**. Each kind opens a page of its
cosmetics:

- **Left-click**: use it, or take it off if you already use it.
- **Right-click** a trail, gadget, pet or balloon: a **preview** for a few seconds (`preview-seconds`) that only you see, even
  for a locked one. The menu closes so you can watch (F5 to look at yourself).
- A cosmetic the player has not unlocked says **Locked**. With `show-locked: false` they are not listed at all.
- A cosmetic the player's Minecraft version cannot show (`min-version`) is not listed for them.

The texts are the `cosmetics-*` lines of `messages.yml`.

## Adding your own

Each cosmetic is a section under `trails:`, `hats:`, `outfits:`, `pets:`, `balloons:` or `gadgets:`. Its name (the section's key, letters, digits,
`_` and `-`) is what is saved for players, so renaming it takes it off everyone who uses it.

Every cosmetic can have:

| Option | |
|---|---|
| `name`, `lore` | Shown in the menu (MiniMessage) |
| `permission` | Needed to use it. Leave it out for `lobby.cosmetic.<kind>.<name>` (e.g. `lobby.cosmetic.trail.notes`); `""` means everyone |
| `min-version` | The oldest Minecraft version that can show it, e.g. `"1.9"`. Older clients do not see it in the menu. Leave it out for every version |

### Trails

```yaml
trails:
  rainbow:
    name: "<gradient:red:blue>Rainbow"
    icon: sugar          # the menu item
    particle: dust       # any particle name
    color: "#ff77ff"     # only for dust
    style: spiral        # feet, halo, spiral, ring, wings or cloud
    amount: 2            # particles per puff (1-20)
    spread: 0.1          # how far they scatter, blocks
    speed: 0             # for particles that move
```

- `feet` puffs at the feet every other tick, and **only while walking**.
- `halo` is a ring of 6 points above the head, 4 times a second, also when standing.
- `spiral` is 2 points circling the body from the feet to the head, also when standing.
- `ring` is 8 points turning on the ground around the feet, also when standing.
- `wings` are two flapping wings on the back. One wing is drawn at a time (9 points), so it fits the usual
  per-viewer limit. `dust` with a `color` looks best.
- `cloud` is a little cloud above the head (`particle`, `amount` of them per puff). `drip: dripping_water` (any
  particle) makes it rain.

Particles that do not exist in Minecraft 1.8 (for example `end_rod`) are shown to 1.8 players as something else by
ViaRewind. Give such trails `min-version: "1.9"` so 1.8 players do not see them in the menu.

### Hats

```yaml
hats:
  crown:
    name: "<gold>Crown"
    material: golden_helmet     # any item or block; a block shows as a block on the head
  creeper:
    name: "<green>Creeper"
    material: player_head
    head-owner: MHF_Creeper     # the game shows this player's skin
  present:
    name: "<red>Present"
    material: player_head
    texture: "eyJ0ZXh0dXJlcyI6..."   # a skin "value" (base64), as head websites give it
```

A hat is a real item on the head, so every player sees it (also those who hide other players' cosmetics), and it
cannot be taken off in the inventory. Hats are put on with the hotbar items, so they need `enabled: true` in
`hotbar.yml`.

### Outfits

```yaml
outfits:
  ocean:
    name: "<blue>Ocean Suit"
    helmet: leather_helmet      # any item; leave out the pieces you do not want
    chest: leather_chestplate
    legs: leather_leggings
    feet: leather_boots
    color: "#3366cc"            # dyes the leather pieces
  king:
    name: "<gold>Golden King"
    chest: golden_chestplate
    legs: golden_leggings
    feet: golden_boots
```

Like a hat, an outfit is real armor, so every player sees it and it cannot be taken off in the inventory. It is only
for looks: lobby protection decides about damage. The helmet is only worn when the player has no hat (the hat
wins). Outfits are put on with the hotbar items too.

### Pets

```yaml
pets:
  dog:
    name: "<white>Puppy"
    icon: bone                  # the menu item
    entity: wolf                # any animal or mob
    baby: true                  # a baby, for animals that have babies (the default)
    name-tag: "<gray>%player_name%'s puppy"   # above it; "" for none
  fox:
    name: "<gold>Fox Cub"
    entity: fox
    min-version: "1.14"         # foxes do not exist before 1.14
```

A pet exists only for the players who see it (packets), like the NPCs: it walks behind its owner, on the ground and
up and down steps, cannot be hit, and costs no server tick time. Give animals that are newer than 1.8 a
`min-version`, so older players do not get something else in its place.

### Balloons

```yaml
balloons:
  red:
    name: "<red>Red Balloon"
    material: red_wool          # a block or a head, like a hat
    leash: true                 # tied to the player's hand with a lead (the default)
  present:
    name: "<gold>Present"
    material: player_head
    head-owner: MHF_Chest
```

A balloon floats above the player and drifts a little behind when they walk. Like pets, it exists only in packets.

### Gadgets

```yaml
gadgets:
  party:
    name: "<yellow>Party Popper"
    type: firework              # firework, leap, burst, paintball, grapple, confetti, tnt or lightning
    cooldown: 15                # seconds between two uses
    colors: ["#ffff55", "#ff55ff"]
    shape: star                 # small_ball, large_ball, star, creeper, burst
  bounce:
    name: "<aqua>Bounce"
    type: leap
    cooldown: 5
    power: 1.5                  # how far it throws (1 = a big jump)
    particle: cloud
    sound: "entity.slime.jump"  # played where it is used ("" = none)
  hearts:
    name: "<red>Hearts"
    type: burst
    power: 2                    # the ring's size in blocks
    particle: heart
```

- **firework**: a firework goes up from the player and explodes in the gadget's colors. It exists only on the
  clients of nearby players, so it costs the server nothing.
- **leap**: throws the player forward and up, with a puff of particles.
- **burst**: a ring of particles around the player.

```yaml
  paint:
    name: "<gold>Paintball Gun"
    type: paintball
    power: 1.5                  # the splash's size in blocks
    range: 30                   # the farthest block it reaches
    duration: 5                 # seconds the paint stays
    blocks: [red_wool, yellow_wool, lime_wool]   # paint colors, one at random per block
  hook:
    name: "<gray>Grappling Hook"
    type: grapple
    power: 1                    # how hard it pulls
    range: 25
    particle: crit              # the rope
  confetti:
    name: "<yellow>Confetti"
    type: confetti
    power: 1.5                  # how much confetti
    colors: ["#ff5555", "#55ff55", "#55ffff"]
  boom:
    name: "<red>Fake TNT"
    type: tnt
    power: 1                    # how hard it pushes players away (0 = not at all)
    sound: "entity.generic.explode"   # played when it explodes
  thunder:
    name: "<yellow>Thunder Rod"
    type: lightning
    range: 40
```

- **paintball**: paints the blocks where the player looks, for every player nearby, for `duration` seconds. Only
  the players' screens change: the map stays as it is, and signs, heads and chests are never painted.
- **grapple**: pulls the player to the block they look at, with a rope of particles.
- **confetti**: shoots colored confetti where the player looks.
- **tnt**: a TNT appears in front of the player, blinks for 2 seconds and explodes. It breaks nothing and hurts
  nobody; players next to it (who see it) are pushed away, unless `power: 0`.
- **lightning**: lightning strikes the block the player looks at, or the player when they look at nothing. Nobody
  is hurt.

A paintball or grapple that has no block in `range` does nothing and costs no cooldown. Right-clicking during the
cooldown shows how long is left, above the hotbar.

## The hotbar

Two items of `hotbar.yml` belong to cosmetics:

```yaml
items:
  gadget:
    slot: 4
    type: gadget          # holds the player's gadget; empty until they pick one
  cosmetics:
    slot: 6
    material: ender_chest
    name: "<light_purple><bold>Cosmetics</bold> <gray>(right-click)"
    actions:
      - "open_menu: cosmetics"
```

Without a `type: gadget` item players can pick a gadget but not use it; `/setup-lobby setup` warns about that.

## Permissions

| Permission | |
|---|---|
| `lobby.command.cosmetics` | `/cosmetics`. Everyone has it without LuckPerms; with LuckPerms give it to the default group |
| `lobby.cosmetic.<kind>.<name>` | Use a locked cosmetic, e.g. `lobby.cosmetic.hat.cake`. `lobby.cosmetic.*` gives all of them |

The menu is opened by the hotbar item without any permission. Without LuckPerms only operators have the locked
cosmetics; set `permission: ""` on the ones everyone should have. A network store gives them like any rank
permission (for example with a LuckPerms command run by the store).

## Where cosmetics are off

- In the [PvP area](pvp.md): no trail, pet, balloon or gadget, and the kit's armor instead of the hat and outfit.
  They come back when the player leaves the area.
- In spectator mode.

## Keeping it cheap

Trails and gadget effects are particles, and pets and balloons are entities that exist only in packets. All of them
are sent from the `lobby-effects` thread, never the server tick:

- only players within `view-distance` blocks (24) get them, and only if they can see the player (the
  [visibility switch](hotbar.md)) and do not hide other players' cosmetics;
- `limits.per-viewer` caps how many particle packets one player receives per tick (10, about 9 KB/s at
  most), and `limits.per-tick` how many the server sends in all (1000). What does not fit is skipped, not
  delayed: a trail that skips a tick looks the same. A different player goes first every tick, so the same ones
  are not always left out.
- pets and balloons move every other tick, and only while their owner moves. One player receives at most 40 of
  their movement packets per tick; a skipped one is caught up by the next, since each says where the pet is.

`/setup-lobby info` shows how busy the effects thread is and how many particle packets the limits dropped. If
players at a crowded spawn see thin trails, that number grows: raise the limits, or lower `view-distance`.

## Placeholders

| Placeholder | Value |
|---|---|
| `%cosmetics_trail%`, `%cosmetics_hat%`, `%cosmetics_gadget%`, `%cosmetics_pet%`, `%cosmetics_balloon%`, `%cosmetics_outfit%` | the name of what the player uses (without colors), or `none` |
| `%cosmetics_unlocked%` | how many cosmetics the player has unlocked |
| `%cosmetics_total%` | how many there are (that the player's version can show) |

## For addons

Addons get `CosmeticChangeEvent` (a cosmetic is put on or taken off; cancel it to keep the old one) and
`GadgetUseEvent` (cancel it to stop the gadget), and `context.cosmetics()` to read and change what players use.
See [addons.md](addons.md).
