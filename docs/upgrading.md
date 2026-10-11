# Upgrading

Changes that need something from you when you update an existing lobby. Newest first.

## Phase 4

- **Operators are stored by UUID in `data/ops.yml`.** The `operators:` list of config.yml is moved there on
  the first start (the log says what was moved) and replaced by a comment. Manage operators with `/op`,
  `/deop` and `/ops`. In standalone offline mode an operator has to confirm with a one-time code from the
  console (`ops.protection: code-and-ip`); see [console-and-ops.md](console-and-ops.md). With LuckPerms,
  operators now have every permission unless `ops.have-all-permissions: false`.
- **Standalone offline mode: players get the vanilla offline UUID** (`OfflinePlayer:<name>`) instead of the
  UUID their client sends, which a client could fake. Settings saved under an old UUID (chat toggle,
  ignore list, visibility) start fresh for players whose client sent a different one.
- **New console.** Tab completion, history and colors (`console.colors`), Ctrl+C stops cleanly. `stop` is
  now a normal command (permission `lobby.command.stop`). Old log archives were named
  `logs/<date>.log.gz`; new ones are `logs/<date>-<n>.log.gz`, made at every start and at midnight.
  `logging.keep-days` (default 14) applies to both. See [console-and-ops.md](console-and-ops.md).
- **New `/announce <message>`** (permission `lobby.command.announce`, click and hover with
  `lobby.command.announce.advanced`) replaces the old `announce` channel. Its look is `announce` in
  messages.yml, its sound and title are `announce:` in chat.yml.
- **One chat, no channels, no private messages.** `channels:`, `default-channel:` and
  `private-messages-enabled:` in chat.yml, `/ch`, the `!`/`#` prefixes and `/msg`/`/r` are gone; chat
  between lobbies and private messages belong to proxy plugins. New option `instance-only` (default
  `true`) keeps chat inside the sender's lobby instance, like the old `local` channel. A message starting
  with `!` or `#` is now sent as written. The `channel` column of `lobby_chat_settings` is left unused and
  new chat log rows have the channel `chat`. Update the bridge plugin on the proxy too: it no longer
  forwards chat between lobbies.
- **Persian/Arabic display fixing is removed** (`/chat persian`, `persian:` in chat.yml, the
  `persian-on`/`persian-off` messages). Texts are shown exactly as written, and every text is rendered
  once per client generation instead of twice. The chat filter still understands Persian and Arabic
  spellings. The old
  `persian:` section and messages are ignored; the `persian` column of `lobby_chat_settings` is left unused.
- **New `commands.yml`.** Players now only see and may type the commands listed for their groups. The
  bundled file lists `/spawn`, `/lobbies`, `/chat` and `/ignore` for everyone. Staff who are not
  operators and have no `lobby.commands.bypass` need a group section, for example
  `mod: { commands: [slowmode, fly] }`. See [commands.md](commands.md). `enabled: false` turns it off.
- **`/lobby` is gone from the lobby server.** On most networks `/lobby` is the proxy's hub command, and the
  two collided.
  - The admin command is now `/setup-lobby` (`reload`, `info`, `setspawn`, ...). The permissions are
    unchanged (`lobby.command.reload`, `lobby.command.info`, `lobby.command.setspawn`).
  - Moving between the lobby instances of this server is `/lobbies <number>`; `/lobbies` alone opens the
    selector. The permission is now `lobby.command.lobbies` (was `lobby.command.lobby`): with LuckPerms,
    give the new one to your default group.
  - Actions that ran `console_command: lobby ...` must use `setup-lobby ...`.
  - In `messages.yml`, `lobby-usage` is now `setup-lobby-usage`. An old file keeps working: missing keys
    use the bundled text.
