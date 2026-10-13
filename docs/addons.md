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

The full guide with an example addon comes with API version 1.0. Until then the API is `0.x`: it may still
change between minor versions, and addons must name the exact minor version they were built for.

### Project setup

Download [`lobby-api.jar`](https://github.com/mohammadhadimohammadi2007-dot/lobby/releases/latest/download/lobby-api.jar) (and
[`lobby-api-sources.jar`](https://github.com/mohammadhadimohammadi2007-dot/lobby/releases/latest/download/lobby-api-sources.jar) for your IDE) from the release your
lobby runs, and compile against it: `compileOnly(files("libs/lobby-api.jar"))`. Or build the API into
your local Maven repository:

```bash
./gradlew :lobby-api:publishToMavenLocal
```

Then in the addon's `build.gradle.kts`:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly("io.github.mohammadhadimohammadi2007-dot:lobby-api:0.2.0")
    compileOnly("net.minestom:minestom:2026.10.05-26.2") // the lobby's Minestom version
}
```

Libraries the addon needs besides those must be inside its jar (shaded).

### addon.json

At the root of the jar:

```json
{
  "id": "parkour-tools",
  "name": "Parkour Tools",
  "version": "1.2.0",
  "main": "com.example.parkour.ParkourAddon",
  "api-version": "0.2",
  "authors": ["Example"],
  "depends": ["economy-core"],
  "soft-depends": ["stats"]
}
```

| Field | Required | |
|---|---|---|
| `id` | yes | lowercase letters, digits and `-`; also the name of its folder |
| `name` | no | shown to admins; the id if missing |
| `version` | yes | the addon's version, any text |
| `main` | yes | the class implementing `LobbyAddon`, with a public constructor without parameters |
| `api-version` | yes | the API version it was built for (`ApiVersion.CURRENT` of the lobby-api jar) |
| `authors` | no | |
| `depends` | no | addons that must be there; they are enabled first and their classes are visible to this addon |
| `soft-depends` | no | enabled first and visible if they are there |

### Lifecycle

```java
public final class ParkourAddon implements LobbyAddon {

    @Override
    public void onLoad(AddonContext context) {
        // Before the lobby reads its files: register placeholders and action types here.
        context.placeholders().register("parkour", params -> ...);
        context.actionTypes().register("parkour_start", argument -> run -> ...);
    }

    @Override
    public void onEnable(AddonContext context) {
        // The lobby runs: commands, events, menus, NPCs, holograms and the other services.
        context.commands().register(new ParkourCommand(), "parkour.command.use");
        context.events().listen(RegionEnterEvent.class, event -> ...);
    }

    @Override
    public void onDisable(AddonContext context) {
        // Only what the addon made itself: everything registered through the context is removed by the lobby.
    }
}
```

`onLoad`, `onEnable` and `onDisable` never run on the tick thread. Event listeners run on the thread
their event names (most on the tick thread); actions and the click handlers of menus run on virtual
threads. Never block the tick thread: use `context.scheduler().async(...)` for files, databases and web
requests, and `context.scheduler().nextTick(...)` to touch players and the world.

### What the API offers

| Area | Through | |
|---|---|---|
| Placeholders | `placeholders()` | `%namespace_...%` everywhere; scopes constant, global, per player, relational, cached like the built-in ones |
| Actions | `actionTypes()` | `type: argument` entries for NPCs, holograms, menus, portals and the hotbar |
| Commands | `commands()` | Minestom commands that follow commands.yml and an optional permission |
| Events | `events()` | join, quit, chat (change or cancel), region enter and leave, NPC, hologram and menu clicks, server connect results, PvP (damage, kills, area, combat tag), and every Minestom event |
| Menus | `menus()` | open menus.yml menus, register menus built in code |
| Holograms and NPCs | `holograms()`, `npcs()` | create, change and remove; saved like the ones made with commands |
| Permissions | `permissions()` | checks and rank (prefix, suffix, group, meta, weight) |
| Player settings | `playerSettings()` | small values per player and addon |
| Network | `network()` | servers, groups and player counts from the bridge; sending players to servers |
| Clients | `clients()` | each player's real Minecraft version (1.8 or modern) |
| PvP | `pvp()` | who is in the [PvP area](pvp.md) or in combat, and players' records |
| Scheduler | `scheduler()` | async, next tick, later, repeating; cancelled when the addon stops |
| Files | `dataFolder()`, `config(name)` | `addons/<id>/`; YAML files copied from the jar on the first run |

### What an addon can see

Each addon has its own class loader. It sees the Java platform, the lobby API, Minestom and the libraries
Minestom's API uses (Adventure, SLF4J, Gson, fastutil), the addons it depends on, and its own jar. The
lobby's internal classes are hidden, so an update of the lobby does not break addons that only use the
API. This is about stability, not security (see the warning at the top).

### Versioning

The API follows semantic versioning on its own version number, independent of the lobby's. From 1.0 on,
an addon built for `1.x` runs on every lobby with API `1.y` where `y >= x`; a new major version may break
addons. While the version is `0.x`, the minor version must match exactly.
