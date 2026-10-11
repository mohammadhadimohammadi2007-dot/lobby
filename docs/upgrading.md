# Upgrading

Changes that need something from you when you update an existing lobby. Newest first.

## Phase 4

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
