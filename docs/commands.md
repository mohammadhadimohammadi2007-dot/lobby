# Commands and who sees them

`commands.yml` decides which commands each group sees in tab completion and may type. Everything else
is left out of the command list the client gets, and typing it gives the same answer as a command that
does not exist ("Unknown or incomplete command.", `unknown-command` in `messages.yml`).

A command allowed in `commands.yml` still needs its own permission: listing `npc` for a group does not let
that group edit NPCs without `lobby.command.npc`.

## Set it up

1. Open `commands.yml`. The bundled file lets every player see `/spawn`, `/lobbies`, `/chat` and
   `/ignore`.
2. Add a section for each group that should see more, named like the LuckPerms group:

   ```yaml
   groups:
     default:
       commands: [spawn, lobbies, chat, ignore]
     mod:
       commands: [slowmode, fly]
     admin:
       commands: ["*"]
   ```

3. Run `/setup-lobby reload`. Everyone online gets the new command list at once.

**Check it worked:** join as a normal player and press `/` then Tab: only the default commands are listed.
Type `/npc`: "Unknown or incomplete command.".

## Who is in which group

- `default` is every player.
- With LuckPerms, a group is the LuckPerms group with the same name. Inherited groups count: if `admin`
  inherits `mod` in LuckPerms, an admin is in both.
- Without LuckPerms, give the permission `lobby.commands.group.<name>` (for example
  `lobby.commands.group.mod`).
- Operators, the console and players with `lobby.commands.bypass` see every command.

A rank change (`/lp user Steve parent set mod`) sends the player a new command list right away.

## When groups disagree

A player gets the commands of all their groups. If two groups disagree about a command, the group with the
highest weight decides: the LuckPerms weight, or `weight:` in the file without LuckPerms. `default` is
always the lowest.

| Entry | Meaning |
|---|---|
| `spawn` | Allows `/spawn`. An alias works too: `hd` allows `/hologram`. |
| `-spawn` | Hides `/spawn`, even if a lower group allows it. |
| `"*"` | Allows every command, except those this same group hides. |

Example: a `muted` group with weight 90 and `commands: ["-spawn"]` hides `/spawn` from muted players even
though `default` allows it.

## Commands from NPCs, menus and items

Commands run by NPCs, menus, portals and hotbar items (`player_command:`) are not limited by
`commands.yml`. You can hide a command from typing and still offer it from a menu. The command's own
permission still applies. A `player_command` the lobby does not have (such as `server bedwars`) runs on the
proxy as the player ([how](npcs.md#sending-players-to-servers-and-proxy-commands)).

## What the lobby cannot hide

Behind Velocity, the proxy adds its own commands (`/server`, `/glist`, and those of proxy plugins) to the
list the client sees. The lobby never sees those commands. Hide them with permissions on the proxy.
