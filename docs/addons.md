# Addons

Addons are jars that extend the lobby without changing its code: new commands, placeholders, actions for
NPCs and menus, menus built in code, reactions to clicks and chat. Bukkit, Spigot and Paper plugins cannot
run on Minestom, so addons are written for this lobby's own API (`lobby-api`).

> **An addon has full access to the server and its data**, like a plugin on Paper: it can read the
> database, see every player and change anything. Only install addons from people you trust. The console
> says so at startup whenever the `addons/` folder has a jar.

## For server owners

### Installing

1. Put the addon's `.jar` in the `addons/` folder next to `lobby-server.jar` (it is created on the first
   start).
2. Restart the lobby. The console lists each addon as it is enabled, or why it could not start.
3. An addon's own files are in `addons/<id>/` (for example `addons/parkour-tools/config.yml`).

Without any jar in `addons/`, the addon system does nothing at all: no listeners, no database table, no
extra work when players join (measured: the join tick is unchanged).

### Commands

| Command | What it does |
|---|---|
| `/setup-lobby addons` | Every addon with its version, authors and state |
| `/setup-lobby addons reload <id>` | Stops the addon, reads its jar again (you can replace the file first) and starts it; then the lobby's files are read again, so NPCs and menus see its new actions and placeholders |

Permission: `lobby.command.setup`. Both work from the console.

A reload is refused while other running addons depend on that addon; restart the server instead.
`/setup-lobby reload` reloads the lobby's own files, not addons.

### When something goes wrong

An addon that throws an error while starting, in a listener, an action or a placeholder is **disabled**,
together with the addons that depend on it. The lobby and the other addons keep running. It stops at once; the console has the
error, and:

- `/setup-lobby addons` shows the addon as failed, with the reason;
- `/setup-lobby doctor` lists every addon that is not running and what to do;
- `/setup-lobby setup` shows "Addons: 2 enabled, 1 not running".

| State | Meaning | What to do |
|---|---|---|
| enabled | Running | |
| failed | Its code threw an error | Send the console log to the addon's author; after an update, `/setup-lobby addons reload <id>` |
| cannot be read | The jar has no valid `addon.json`, or its main class is missing | It is not an addon for this lobby, or the download is broken |
| incompatible | Made for another API version | Get a version of the addon made for this lobby version |
| missing a dependency | Needs another addon that is not there or did not start | Install that addon too |
| dependency loop | Addons that each need the other first | Report it to their authors |

Actions of a disabled addon do nothing until the next reload; after `/setup-lobby reload` they are reported
as unknown actions (and kept in the files).

### Where addons keep player data

Addons can save small values per player. With a database configured (`integrations.yml`) they are in the
table `<prefix>addon_settings` (the prefix of `chat.yml` `storage.table-prefix`, `lobby_` by default), so
all lobbies of the network share them; otherwise in `data/addon-settings/<addon>/<uuid>.json`.

## For addon developers

The full guide is [addon-development.md](addon-development.md): project setup, `addon.json`, the lifecycle,
everything the API offers, working with other addons, and the compatibility promise of API 1.0. The
[example addon](../examples/addon-example) is a complete, commented addon to start from.