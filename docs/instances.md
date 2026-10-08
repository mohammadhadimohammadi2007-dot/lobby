# Several lobbies on one server

One server can run several copies of the same map, called **lobby instances**. Players in different
instances do not see each other, so 300 players feel like three quiet lobbies of 100 instead of one
crowded one. The map is loaded **once** and shared by every instance, so a second lobby costs almost no
memory and no extra loading time.

By default there is exactly one instance and everything behaves like a normal server.

## Turning it on

In `config.yml`:

```yaml
lobbies:
  # 1 to 50, or "auto" (one instance per players-per-instance of server.max-players)
  instances: 3
  players-per-instance: 50
  # least-players (spread the load) or fill-first (lobby 1 fills up first)
  join: least-players
```

| Option | What it does |
|---|---|
| `instances: <number>` | Always that many instances |
| `instances: auto` | `server.max-players` divided by `players-per-instance`, rounded up (at least 1, at most 50) |
| `players-per-instance` | How many players one instance should hold. Used by `auto`, when choosing where a joining player goes, and by `/lobby <number>` |
| `join: least-players` | A joining player gets the emptiest instance. With one limit for all of them that is also the one with the most free space |
| `join: fill-first` | A joining player gets the first instance that still has room |

The number of instances is read at startup: changing it needs a restart, and `/lobby reload` says so.
`join` and `players-per-instance` are read live.

`server.max-players` is still the limit for the **whole server**; `players-per-instance` only decides
how players are spread over the lobbies.

## Moving between lobbies

| | |
|---|---|
| `/lobby <number>` | Moves the player to that lobby. Permission: `lobby.command.lobby` (everyone by default) |
| `/lobbies` | Opens the lobby selector: one item per lobby, with its player count; the player's own lobby glows |
| `lobby: <number>` | The action for NPCs, menu items, hotbar items and portals |
| `open-menu: lobbies` | Opens the same selector from any action list |

A player may not join an instance that already holds `players-per-instance` players, unless they have
`lobby.lobbies.join-full`.

The selector's texts come from `messages.yml` (`lobby-selector-*`), where `<number>` is the lobby's
number and `<players>` how many players are in it. The item layout itself is built by the server,
because the number of lobbies is only known while running. To build your own selector instead, put a
menu in `menus.yml` with one item per lobby and `lobby: <number>` as its action.

## Chat

A channel in `chat.yml` can stay inside the sender's own lobby:

```yaml
channels:
  local:
    instance-only: true    # only players in the same lobby instance see it
```

The bundled `local` channel does this, so with several instances each lobby has its own local chat.
Every other channel (`global`, `staff`, ...) reaches the whole server, and `network: true` channels
reach the whole network. With one instance, `instance-only` changes nothing.

## What else is shared

| | |
|---|---|
| The map and its blocks | Loaded once, shared by every instance. Changing a block changes it for all of them |
| Holograms, NPCs, portals, jump pads | Shown in every instance automatically: they are sent as packets to the players who are near them, not placed in one world |
| Time of day and weather | Set per instance from the same `world:` settings, so they look the same everywhere |
| Players, mobs and dropped items | Per instance: that is the point |
| Permissions, chat settings, mutes, the player limit | Per server, as before |

## Placeholders

`%lobby_id%` (which lobby a player is in), `%lobby_count%`, `%lobby_online%` and
`%lobby_online_<number>%`. See [placeholders](placeholders.md).

## Things to know

- Old clients are fine. Moving between instances makes the client load the world again, so players see
  a short loading moment, the same as being teleported to another world on a Paper server.
- A player who falls into the void stays in their own lobby: they are teleported back to spawn there.
- 50 instances is the hard limit, because that is what a selector menu can show.
