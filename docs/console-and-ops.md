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
