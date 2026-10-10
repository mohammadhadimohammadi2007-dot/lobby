# Scoreboard, tab list, nametags, boss bar and titles

Everything here is set in `display.yml` and applies with `/lobby reload`. Texts are MiniMessage
(`<gold>`, `<gradient:...>`, `<#ff8800>`) or `&` colour codes, with [placeholders](placeholders.md).

## Old clients (1.8-1.12)

Old clients only know 16 colours and have length limits. Every text can have a `legacy` version for
them. Without one, the normal text is used with its colours mapped to the nearest of the 16 and cut to
the limit:

| | Limit on 1.8-1.12 |
|---|---|
| Scoreboard line | 32 characters (two halves of 16), colour codes included |
| Scoreboard title | 32 characters |
| Nametag prefix / suffix | 16 characters each |

## Scoreboard

```yaml
scoreboard:
  enabled: true
  boards:
    default:
      priority: 0
      permission: ""            # "" = everyone
      title: ["<gold><bold>LOBBY", "<yellow><bold>LOBBY"]   # more than one frame animates it
      title-interval: 10        # ticks per frame
      update-interval: 20       # how often lines are refreshed, in ticks
      lines:
        - ""                    # an empty line is a spacer
        - "<white>%player_name%"
        - text: "<gray>Ping: %player_ping%ms"
          interval: 40          # this line every 2 seconds
          legacy: "&7Ping: %player_ping%"
      # legacy-title: ["&6&lLOBBY"]
      # legacy-lines: [...]     # a whole other set of lines for 1.8-1.12
```

- Each player sees the board with the **highest priority** whose permission they have.
- At most 15 lines.
- Only lines whose text really changed are sent. A board where only the player count moves costs one
  small packet per player per second.
- How each client gets its lines:
  - **1.20.3 and newer**: each line is the score's display name, and the red numbers on the right are
    hidden;
  - **1.13 to 1.20.2**: each line is a team prefix, because these clients would otherwise show the
    line's internal name. The red numbers cannot be hidden on these versions;
  - **1.8 to 1.12**: the same, with the line split over the 16-character prefix and suffix. The colour
    carries over the split.

## Tab list

```yaml
tab:
  enabled: true
  update-interval: 20
  header: ["<gold><bold>EXAMPLE NETWORK", "<gray>Online: %server_online%"]
  footer: ["<gray>Ping: %player_ping%ms"]
  name-format: "%luckperms_prefix%%player_name%%luckperms_suffix%"
  show: server                  # or "instance": only players of the same lobby instance
  group-order: [owner, admin, mod, helper, vip, default]
```

- **Sorting.** Players are sorted by the weight of their LuckPerms group, highest first (`/lp group
  admin setweight 100`). A group without a weight is placed by `group-order`, first listed first;
  without LuckPerms, `group-order` decides alone. Players of equal rank are sorted by name, ignoring case.
- **Names.** Each name is rendered once per refresh, plus once more for 1.8-1.12. Only names that
  changed are sent, all in one packet.
- **`show: instance`.** Players of other instances are taken out of the tab list.

## Nametags

```yaml
nametags:
  enabled: true
  prefix: "%luckperms_prefix%"
  suffix: "%luckperms_suffix%"
  name-color: auto              # the prefix's last colour, or white, red, gold...
```

The nametag and the tab position come from one team per player. All teams are made by the lobby's
single team manager, so nametags, tab sorting and NPC names never get in each other's way.

## Boss bar and action bar

```yaml
bossbar:
  enabled: true
  interval: 200                 # ticks per message
  messages:
    - text: "<yellow>Welcome, %player_name%!"
      color: yellow             # pink, blue, red, green, yellow, purple, white
      style: progress           # progress, notched_6, notched_10, notched_12, notched_20
      progress: 1.0
      permission: ""            # "" = everyone
action-bar:
  enabled: false
  interval: 100
  messages:
    - text: "<gray>Use <yellow>/lobby</yellow> to change lobby"
```

Each player rotates through the messages they have the permission for.

## Join title

```yaml
join-title:
  enabled: true
  title: "<gold><bold>Welcome"
  subtitle: "<gray>%player_name%"
  fade-in: 10
  stay: 60
  fade-out: 20
  delay: 20                     # ticks after joining
```

## Performance

All of this runs on its own thread, `lobby-board`, never on the server tick, and apart from the
holograms and NPCs (thread `lobby-display`): a busy moment there, such as many players walking past many
NPCs, never makes the scoreboard late. `/lobby info` shows how busy each of the two threads is, and how
many scoreboard refreshes were skipped because the previous one was still running.

Players take turns within each interval: with a one-second line, a tenth of the players are updated in
each of the second's ten refreshes, instead of all of them at once. Holograms and NPCs with the same
update interval are spread the same way.

Measured with 200 players, 50 holograms and 30 NPCs (some with per-player text), and this scoreboard,
tab list, nametags and boss bar, with values that change every second:

| | |
|---|---|
| Display thread busy | 15-18% on average, 25-38% in the worst second |
| Hologram/NPC refresh (4 a second) | median 24-29 ms |
| Scoreboard/tab/bars refresh (10 a second) | median 2.5 ms |
| One player joining 200 | about 50 ms of display-thread work |
| Allocation on the display thread | about 85 MB per second of work (baseline for later tuning) |
| Garbage collection | about 150-160 ms over 20 seconds, 27-30 collections, 512 MB test heap |

And the whole lobby for five minutes (`LobbyLoadTest`): 200 players walking, the same holograms and NPCs,
the bundled scoreboard, tab list, nametags and boss bar, hotbar, visibility, regions, pads and portals:

| | |
|---|---|
| Server tick (MSPT) | average 1.70 ms, p99 5.4 ms, max 28.3 ms; no tick over 50 ms; no growth (1.81, 2.01, 1.39, 1.56, 1.74 ms per minute) |
| Holograms/NPCs thread (`lobby-display`) | 25-26% busy; longest single cycle 210-590 ms |
| Scoreboard/tab thread (`lobby-board`) | 3.5-4.6% busy; longest single cycle 32-43 ms |
| Scoreboard refreshes skipped | 0 in the five minutes (19 while 200 players joined one per tick); 173 when both shared one thread |
| Allocation (whole JVM) | about 228 MB/s; GC 2.4% of the time with a 512 MB heap |

What one join costs the server tick in a full lobby (`JoinCostTest`: the real lobby wiring, 199 players
walking, 40 joins two seconds apart, each player leaving again):

| | |
|---|---|
| Join tick (Minestom letting the player in, plus the rest of that tick) | median 3.8 ms, max 17 ms |
| With a join every 0.45 s (400 joins) | median 5.7 ms, max 36 ms |
| First join after a start | about 140 ms (one-time class loading and compiling) |
