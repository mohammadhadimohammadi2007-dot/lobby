# Console and operators

## The console

The server console works like Spigot and Paper:

- **Tab completion** from the real commands: command names, subcommands, fixed words such as NPC poses,
  and names the commands suggest themselves (NPCs, holograms, portals).
- **History**: arrow up/down, kept between restarts in `logs/.console_history`.
- **Colors**: warnings are yellow, errors red. `console.colors` in `config.yml`: `auto` (colors only in a
  real terminal), `on` or `off`.
- **Log lines never break your typing**: they are printed above the prompt.
- **Ctrl+C** stops the server cleanly, the same as `stop`: everything is saved first.

Commands can be typed with or without `/`. They run on the server thread, like a player's.

When the server is not started in a terminal (Pterodactyl, Docker, systemd, `nohup`, a pipe), there is no
prompt and no color: commands are read line by line, and the output is plain text without color codes.
Pterodactyl's console still sends what you type to the server.

**Check it worked:** start the server in a terminal, type `setup-` and press Tab: it completes to
`setup-lobby`. Press Tab again after a space: `reload`, `info`, `setspawn`... are offered.

## Log files

| File | What it is |
|---|---|
| `logs/latest.log` | The log of the current run, without color codes |
| `logs/2026-10-11-1.log.gz` | Earlier logs. `latest.log` is archived at every start and at midnight; the number counts the archives of that day |
| `logs/chat/` | The chat log (see [chat.md](chat.md)); it has its own retention |

`logging.keep-days` in `config.yml` (default 14) decides how many days of archives are kept; older ones are
deleted at start, at midnight and after `/setup-lobby reload`.

## Operators

Operators have every permission, like on Bukkit and Paper. They are stored by UUID in `data/ops.yml`,
with their last known name.

### Make someone an operator

From the console (or as an operator, in game):

```
op Notch          make Notch an operator
deop Notch        take it away again
ops               list the operators and their state
```

**Check it worked:** `ops` lists the player. When they join, `/setup-lobby info` works for them.

Which UUID the lobby uses for a name depends on the mode:

| Mode | UUID for `op <name>` while the player is offline |
|---|---|
| Standalone, online mode | Their Mojang account (looked up; an unknown name is refused) |
| Standalone, offline mode | The offline UUID, `OfflinePlayer:<name>`, like vanilla and Bukkit |
| Behind Velocity or BungeeCord | Not known yet: the entry waits as `pending-<name>` and gets the UUID the proxy forwards on their first join |

If the player is online, the UUID of their connection is used.

### LuckPerms

With LuckPerms on, operators still have every permission. Set `ops.have-all-permissions: false` in
`config.yml` to give operators only what LuckPerms gives them. The console always has every permission.

### Offline mode: protecting operators

In offline mode anyone can join with **any** name, including yours, and the client also chooses its own
UUID. So in standalone offline mode a name is never enough to be an operator:

- The lobby gives every player the offline UUID of their name, whatever the client sent.
- With `ops.protection: code-and-ip` (the default), an operator who joins from an address they have not
  confirmed is **not** an operator. The console prints a one-time code:

  ```
  Steve is an operator, but joined from 203.0.113.7, which they have not confirmed, so they are NOT an
  operator for now. If this really is Steve, tell them to type: /op confirm 482913 (valid 10 minutes).
  ```

  They type `/op confirm 482913` in game and get their rights; that address works from then on. The code
  expires after 10 minutes and after 5 wrong tries. A known address works right away.
- With `code`, the code is needed only once; after that any address works.
- With `off`, a name is enough. The console warns about this at every start. Only use it on a server
  nobody else can reach.

Opping a player who is online (`op Steve` while Steve is connected) confirms their current address.

Behind a proxy, the proxy checks names, so this protection is off. If your proxy runs in offline
(cracked) mode, set `ops.protect-behind-proxy: true`; the proxy must then forward the real player
addresses for `code-and-ip`.

To make someone confirm again, remove their `ips` in `data/ops.yml` (or set `confirmed: false`) and run
`/setup-lobby reload`.

### Moving from the old `operators:` list

Older versions kept operators as names in `config.yml` (`operators: [...]`). On the first start the lobby
moves them to `data/ops.yml`, replaces the list with a comment and logs what it did. In offline mode each
moved operator confirms once with a code on their first join.

## Which command belongs to whom

Behind a proxy, a command typed in game first reaches the proxy. Commands of proxy plugins never reach the
lobby. The lobby's own console always runs the lobby's command.

| Command | Handled by | Notes |
|---|---|---|
| `/server`, `/glist`, `/send`, `/velocity` | Velocity | |
| `/kick`, `/ban`, `/tempban`, `/mute`, `/warn` | LiteBans (or another punishment plugin) on the proxy | The lobby has no kick, ban or whitelist of its own |
| `/whitelist` | The proxy (Velocity has no built-in whitelist; use a proxy plugin) | |
| `/msg`, `/r`, `/tell` | A proxy plugin | Private messages are not part of the lobby |
| `/lobby`, `/hub` | Usually a proxy plugin (send to a lobby server) | The lobby's admin command is `/setup-lobby` |
| `/op`, `/deop`, `/ops`, `/stop`, `/setup-lobby` | This lobby | Also from this lobby's console |
