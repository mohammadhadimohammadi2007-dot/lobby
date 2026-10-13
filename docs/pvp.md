# PvP

A fighting area in the lobby. Players who walk in get a kit and can fight each other; walking out gives
their lobby items back. Nobody really dies: the last hit sends the player back to the area's spawn with a
new kit and counts a kill. Kills, deaths and streaks are saved, with placeholders and leaderboards.

## Setting it up

As an operator (permission `lobby.command.pvp`):

1. `/setup-lobby wand`, then hit one corner of the area and right-click the opposite corner (the outline is
   shown with particles). Include enough height for jumping.
2. `/pvp setregion`: the selection becomes the PvP area.
3. Stand where players should come back after dying: `/pvp setspawn`. Without it they come back in the
   middle of the area's floor.
4. Put the kit in your inventory (armor on, shield in the off hand if you like) and run `/pvp kit`. Items keep
   everything: enchantments, names, colors.

| Command | What it does |
|---|---|
| `/pvp setregion` | The setup wand's selection becomes the area (the spawn is kept if it is still inside) |
| `/pvp setspawn` | Where players come back after dying; you must stand inside the area |
| `/pvp kit` | Your inventory and armor become the kit |
| `/pvp info` | Size, spawn, kit, how many are fighting, combat mode |
| `/pvp delete` | Removes the area; everyone in it gets their lobby items back |

Everything is saved in `data/pvp.yml`; the rules are in `pvp.yml` (below). `/setup-lobby setup` shows what is
missing.

**Check it worked:** walk in: you get the kit and lose double jump. Walk out: your lobby items come back.

## How fights work

- Only players who are **both inside** the area can hurt each other, and only in survival or adventure mode
  (an admin in creative mode can walk through safely).
- Inside the area there is no double jump and no `/fly`, players do not drop items, and every fighter sees
  every other fighter, whatever they chose with the visibility item.
- **The attacker's game version decides how a hit works:**

| Attacker plays | Combat |
|---|---|
| 1.8 (through ViaRewind) | 1.8: no attack cooldown (click as fast as you like), 1.8 weapon damage (diamond sword 8), critical hits when falling, 1.8 armor and knockback |
| 1.19.4 or newer | Modern: damage depends on how charged the swing is (wait for the cooldown), sweep attacks with swords, critical hits only when falling without sprinting, armor toughness, modern knockback |

  Both have Minecraft's short protection after a hit (half a second in which only a stronger hit counts), so
  fast clicking does not multiply the damage. Sharpness, Knockback, Sweeping Edge and Protection work.
- In a mixed fight the 1.8 player's fast clicking is usually stronger. With `same-version-only: true` in
  pvp.yml, 1.8 players only fight 1.8 players and modern players only modern ones.
- The real version is only known behind a proxy with ViaVersion and the bridge (see
  [client versions](client-versions.md)); without it everyone counts as modern.

## Combat tag

A hit puts the attacker and the defender **in combat** for `combat-tag-seconds` (10 by default); a new hit
starts it again. Above the hotbar they see how long it lasts. While in combat they cannot:

- walk out of the area (they are stopped at the edge);
- switch servers through NPCs, portals or menus;
- use `/server` or other plugins' server switching on the proxy (needs lobby-bridge 0.6 or newer);
- switch lobbies, or use the commands in `combat-blocked-commands`.

Leaving the game while in combat counts as a death, and as a kill for the last attacker
(`combat-log-counts-as-death`).

## pvp.yml

| Option | Default | |
|---|---|---|
| `enabled` | `true` | `false`: nobody can be hurt, even with an area set |
| `same-version-only` | `false` | Only players on the same kind of version fight each other |
| `combat-tag-seconds` | `10` | 0-120; 0 turns the combat tag off |
| `combat-blocked-commands` | `[spawn, lobbies, fly]` | Commands blocked while in combat |
| `area-blocked-commands` | `[fly]` | Commands blocked inside the area |
| `combat-log-counts-as-death` | `true` | Quitting in combat counts as a death |
| `sweep-attacks` | `true` | Modern sword sweeps hit players next to the target |
| `max-reach` | `6.0` | Hits from further away (blocks) are ignored |
| `kill-actions`, `death-actions` | a sound | [Actions](npcs.md#clicks) run for the killer and for who died |
| `leaderboard-size`, `leaderboard-refresh` | `10`, `60` | Places of the leaderboard placeholders, and seconds between reading them again |

`kill-actions` can give rewards, for example `console_command: give %player_name% ...` or, through the
bridge, `proxy_command:` for a network plugin.

## Stats and placeholders

Kills, deaths, the current streak and the best streak are saved per player: in the table
`<prefix>pvp_stats` of the shared database (the prefix of chat.yml `storage.table-prefix`, `lobby_` by default),
so all lobbies count together, or in `data/pvp-stats.json` without a database.

| Placeholder | Value |
|---|---|
| `%pvp_kills%`, `%pvp_deaths%` | the player's kills and deaths |
| `%pvp_streak%`, `%pvp_best_streak%` | kills since their last death, and the longest streak ever |
| `%pvp_kdr%` | kills per death, two decimals |
| `%pvp_in_area%`, `%pvp_in_combat%` | `yes` or `no` |
| `%pvp_combat_seconds%` | seconds of combat left |
| `%pvp_players%` | players in the area now |
| `%pvp_top_<board>_<place>_name%`, `%pvp_top_<board>_<place>_value%` | leaderboards; `<board>` is `kills`, `deaths`, `streak` or `best_streak`, `<place>` 1 to `leaderboard-size` |

A leaderboard hologram:

```
/hologram create pvptop
/hologram setline pvptop 1 <gold><bold>Top killers
/hologram addline pvptop <yellow>1. %pvp_top_kills_1_name% <gray>- %pvp_top_kills_1_value%
/hologram addline pvptop <yellow>2. %pvp_top_kills_2_name% <gray>- %pvp_top_kills_2_value%
```

Leaderboard placeholders are the same for everyone, so such a hologram is built once for all players.
An empty place shows `---` and `0`.

## Messages

The `pvp-*` lines of messages.yml: entering and leaving, the kill announcement (`<killer>`, `<victim>`,
`<streak>`, to everyone in the area), the combat tag above the hotbar, and `pvp-proxy-blocked`, which the proxy
shows when it refuses `/server` (MiniMessage with `<seconds>`, without `<prefix>`).

## For addons

Addons get `PvpDamageEvent` (change or cancel a hit), `PvpKillEvent`, `PvpAreaEnterEvent`,
`PvpAreaLeaveEvent` and `CombatTagEvent`, and `context.pvp()` for who is in the area or in combat and
players' records. See [addons.md](addons.md).
