# Writing addons

An addon is a jar in the lobby's `addons/` folder that adds to the lobby without changing its code: commands,
placeholders, actions for NPCs and menus, menus built in code, regions, reactions to clicks, chat and joins, and
services for other addons. Bukkit, Spigot and Paper plugins cannot run on Minestom; addons are written for this
lobby's own API, `lobby-api`. For installing addons see [addons.md](addons.md).

The API is **version 1.0** and follows a written [compatibility promise](#the-compatibility-promise): an addon
built today keeps working on every 1.x lobby.

- [Quick start](#quick-start)
- [Project setup](#project-setup)
- [addon.json](#addonjson)
- [Lifecycle and threads](#lifecycle-and-threads)
- [What the API offers](#what-the-api-offers)
- [Working with other addons](#working-with-other-addons)
- [The compatibility promise](#the-compatibility-promise)
- [Testing and debugging](#testing-and-debugging)
- [For lobby maintainers: changing the API](#for-lobby-maintainers-changing-the-api)

## Quick start

The [example addon](../examples/addon-example) is a complete, commented addon (a click counter) that uses most of
the API, and a template to start from: copy its folder and follow
[Using it as a template](../examples/addon-example/README.md#using-it-as-a-template). The lobby's tests start it
in a real lobby, so it always matches the current API.

The smallest addon is one class and an `addon.json`:

```java
package com.example.hello;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.LobbyJoinEvent;

public final class HelloAddon implements LobbyAddon {

    @Override
    public void onEnable(AddonContext context) {
        context.events().listen(LobbyJoinEvent.class, event ->
                context.texts().send(event.getPlayer(), "<prefix><green>Hello, %player_name%!"));
    }
}
```

```json
{ "id": "hello", "version": "1.0.0", "main": "com.example.hello.HelloAddon", "api-version": "1.0" }
```

## Project setup

You need JDK 25. The addon compiles against `lobby-api` and Minestom, both `compileOnly`: the lobby provides them
at runtime, so they must not be inside the addon's jar.

```kotlin
// build.gradle.kts
plugins {
    java
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly("io.github.mohammadhadimohammadi2007-dot:lobby-api:1.0.0")
    compileOnly("net.minestom:minestom:2026.10.05-26.2") // the lobby's Minestom version
}
```

Where the API comes from:

- **From a release**: every [release](https://github.com/mohammadhadimohammadi2007-dot/lobby/releases/latest)
  has `lobby-api.jar`, `lobby-api-sources.jar` and `lobby-api-javadoc.jar`. Put them in `libs/` and use
  `compileOnly(files("libs/lobby-api.jar"))`.
- **From the lobby's source**: `./gradlew :lobby-api:publishToMavenLocal` puts it in your local Maven repository
  (`mavenLocal()` above).

Other libraries the addon needs (a database driver, a JSON library other than Gson...) go inside its jar (shade
them, and relocate them if another addon might bring a different version).

## addon.json

At the root of the jar:

```json
{
  "id": "parkour-tools",
  "name": "Parkour Tools",
  "version": "1.2.0",
  "main": "com.example.parkour.ParkourAddon",
  "api-version": "1.0",
  "authors": ["Example"],
  "depends": ["economy-core"],
  "soft-depends": ["stats"]
}
```

| Field | Required | |
|---|---|---|
| `id` | yes | lowercase letters, digits and `-`; also the name of its folder, `addons/<id>/` |
| `name` | no | shown to admins; the id if missing |
| `version` | yes | the addon's own version, any text |
| `main` | yes | the class implementing `LobbyAddon`, with a public constructor without parameters |
| `api-version` | yes | the API version it needs, `major.minor`: the oldest lobby API it runs on (see [the promise](#the-compatibility-promise)) |
| `authors` | no | |
| `depends` | no | addons that must be there; they start first and their classes are visible to this addon |
| `soft-depends` | no | started first and visible if they are there |

## Lifecycle and threads

```java
public final class ParkourAddon implements LobbyAddon {

    @Override
    public void onLoad(AddonContext context) {
        // Before the lobby reads its files: placeholders, action types and services go here, so the NPCs,
        // holograms and menus read right after can use them.
    }

    @Override
    public void onEnable(AddonContext context) {
        // The lobby runs: commands, events, menus, NPCs, holograms, regions and every other service.
    }

    @Override
    public void onDisable(AddonContext context) {
        // Only what the addon made itself (its own threads, connections, files).
    }
}
```

- `onLoad` runs for every addon, then the lobby reads its files, then `onEnable` runs for every addon. Addons start
  after the addons they depend on, and stop before them.
- **Everything registered through the context is removed when the addon stops**: listeners, commands, tasks,
  placeholders, action types, menus, regions and services. `onDisable` does not need to undo them.
- An exception in any of these methods, or in a listener, action or placeholder, **disables the addon** (and the
  addons that depend on it) with the error in the log. The lobby and the other addons go on.
- `onLoad`, `onEnable` and `onDisable` never run on the tick thread. Most events are fired on the tick thread
  (each event class says), actions and menu click handlers run on virtual threads, placeholders on the thread
  that draws the text.
- **Never block the tick thread.** Files, databases and web requests go through `context.scheduler().async(...)`;
  changes to players and the world go back with `context.scheduler().nextTick(...)`.

Admins reload one addon with `/setup-lobby addons reload <id>` (refused while other addons depend on it).

## What the API offers

Everything is reached through the `AddonContext` given to each method:

| Area | Through | |
|---|---|---|
| Placeholders | `placeholders()` | `%namespace_...%` in every text of the lobby; constant, global, per player or relational, cached like the built-in ones |
| Actions | `actionTypes()` | `type: argument` entries for NPCs, holograms, menus, portals and hotbar items |
| Commands | `commands()` | Minestom commands that follow `commands.yml` and an optional permission |
| Events | `events()` | the lobby's events, every Minestom event, other addons' events; priorities; firing your own |
| Messages | `texts()` | MiniMessage with placeholders and the `<prefix>` of messages.yml |
| Menus | `menus()` | open menus.yml menus, register menus built in code |
| Holograms, NPCs | `holograms()`, `npcs()` | create, change and remove; saved like the ones made with commands |
| Regions | `regions()` | which areas a player is in; the addon's own areas, which fire region events |
| Permissions | `permissions()` | checks and rank (prefix, suffix, group, meta, weight) |
| Player settings | `playerSettings()` | small values per player, in the lobby's database (shared by the network) or files |
| Network | `network()` | servers, groups and player counts from the proxy bridge; sending players to servers |
| Clients | `clients()` | each player's real Minecraft version (1.8 or modern) |
| PvP | `pvp()` | who is in the [PvP area](pvp.md) or in combat, and players' records |
| Cosmetics | `cosmetics()` | what players use of the [cosmetics](cosmetics.md), putting them on or off |
| Vanish | `vanish()` | who is [vanished](vanish.md), who sees them, vanishing players |
| Services | `services()` | services addons offer each other |
| Scheduler | `scheduler()` | async, next tick, later, repeating; cancelled when the addon stops |
| Files | `dataFolder()`, `config(name)` | `addons/<id>/`; YAML files copied from the jar on the first start |

The javadoc of each interface (in `lobby-api-javadoc.jar`, or your IDE with the sources jar) has the details.

### Placeholders

```java
context.placeholders().register("parkour", params -> switch (params) {
    case "best" -> PlaceholderValue.perPlayer(Duration.ofSeconds(5), player -> times.best(player));
    case "record" -> PlaceholderValue.global(Duration.ofSeconds(30), () -> times.record());
    default -> null; // not ours: shown as written
});
```

Pick the narrowest scope: a hologram line with only global placeholders is built once for everyone, one per-player
placeholder makes it built for each player. Never return text a player typed as `formatted`. The functions must be
quick: read from your own cache and refresh it with the scheduler.

### Actions

```java
context.actionTypes().register("parkour_start", argument -> {
    Course course = courses.get(argument);          // checked once, when the file is read
    if (course == null) {
        throw new InvalidActionException("no course '" + argument + "'");   // shown to the admin
    }
    return run -> {
        course.start(run.player());
        return ActionResult.CONTINUE;               // or STOP, or waitTicks(20)
    };
});
```

Then an NPC, menu item, portal or hotbar item can have `parkour_start: forest`. Prefix types with your addon's
name to avoid clashes.

### Events

```java
context.events().listen(NpcClickEvent.class, event -> { ... });                         // NORMAL
context.events().listen(LobbyChatEvent.class, EventPriority.EARLY, event -> { ... });    // before the others
context.events().listen(PvpKillEvent.class, EventPriority.MONITOR, event -> { ... });    // see the final outcome
```

The lobby's events are in `io.github.mohammadhadimohammadi2007_dot.lobby.api.event`: join and quit, chat (change
or cancel the message), region enter and leave, NPC, hologram and menu clicks, server connect results, PvP
(damage, kills, area, combat tag), cosmetics (put on or off, gadget used) and vanish. Every Minestom event works
too. Priorities order listeners across all addons: `EARLY`, `NORMAL`, `LATE`, `MONITOR` (look, do not change).

Addons can have **events of their own**: extend `LobbyPlayerEvent` (or `CancellableLobbyEvent`), and fire them:

```java
MilestoneEvent event = context.events().fire(new MilestoneEvent(player, clicks));
if (event.isCancelled()) { ... }   // for a cancellable event
```

### Messages

```java
context.texts().send(player, "<prefix><green>You finished in <white>%parkour_best%</white>!");
Component line = context.texts().render("<gold>%player_name%", player);
context.texts().broadcast("<prefix><yellow>A new record!");
```

`<prefix>` is the server owner's prefix from messages.yml, so addon messages look like the lobby's own. Better
still, read your texts from your `config.yml`, so server owners can translate them.

### Regions

```java
Regions.Region start = context.regions().define("start", new Vec(10, 64, 10), new Vec(14, 70, 14));
context.events().listen(RegionEnterEvent.class, event -> {
    if (event.owner().equals("addon:" + context.description().id()) && event.region().equals("start")) { ... }
});
boolean atPortal = context.regions().at(player).stream().anyMatch(region -> region.owner().equals("portal"));
```

An addon's regions are owned by `addon:<id>`. Every lobby instance shares the map, so a region exists in all of
them.

### Player settings

```java
context.playerSettings().set(player.getUuid(), "best-time", "41.2");
context.playerSettings().get(player.getUuid(), "best-time").thenAccept(saved -> ...);
```

Keys are per addon, values up to 4096 characters. With a database the values are shared by every lobby of the
network. Neither method blocks; for bigger data use your own storage (on `scheduler().async`).

### Commands, menus, holograms and NPCs

```java
Command command = new Command("parkour");
command.setDefaultExecutor((sender, ignored) -> ...);
context.commands().register(command, "parkour.command.use");   // permission, or none

context.menus().register("parkour-courses", player -> new MenuView("<green>Courses", 3, List.of(
        MenuButton.withActions(11, Material.OAK_SAPLING, "<green>Forest", List.of(), List.of("parkour_start: forest")),
        MenuButton.of(15, Material.CLOCK, "<yellow>Your best", List.of("<gray>%parkour_best%"), player -> ...))));

if (!context.holograms().exists("parkour-top")) {
    context.holograms().create("parkour-top", new Pos(0, 66, 5), List.of("<gold>Best: %parkour_record%"));
}
```

`commands.yml` decides which players see and may type a command: tell server owners to add it to a group there.
Holograms and NPCs are saved like the ones admins make; create them once (check `exists`) and let admins move
them.

## Working with other addons

**Depend** on an addon (`"depends": ["economy"]` in addon.json) to start after it and see its classes.

**Services** let one addon offer something to the others without them knowing its classes:

```java
// in the economy addon, which publishes the Economy interface:
context.services().provide(Economy.class, new MyEconomy());

// in a shop addon that depends on it:
context.services().get(Economy.class).ifPresent(economy -> economy.take(player, 100));
```

Only one addon can provide a type. Look the service up when you need it rather than keeping it: its provider may
be reloaded or stop, and its services go with it. A service provided in `onLoad` can be used by the addons that
depend on it from their own `onLoad` on.

**Events** of one addon can be listened to by the addons that depend on it, like the lobby's events.

The example addon provides a `ClickCounter` service and fires a `ClickMilestoneEvent`; the lobby's tests have a
second addon that uses both.

## The compatibility promise

The API has its own version, `major.minor.patch` (`ApiVersion.CURRENT`), separate from the lobby's version.

- **An addon built for API `1.x` runs on every lobby with API `1.y` where `y >= x`.** Its `api-version` says the
  oldest version it needs.
- A **minor** version (1.1, 1.2...) only adds: new interfaces, methods, events, enum constants. Nothing an addon
  uses is removed or changed.
- A **major** version (2.0) may remove or change things. Before that, they are deprecated
  (`@Deprecated(since = "1.x", forRemoval = true)`) for at least one minor version, with the new way to do it
  named in the javadoc.
- A **patch** version (1.0.1) only fixes the lobby's behaviour; the API stays the same.

What that covers:

| Marked | Means |
|---|---|
| `@NonExtendable` | Implemented by the lobby only: call it, never implement it. New methods may appear in any minor version |
| no mark, interface | Addons implement it (`LobbyAddon`, `ActionFactory`, `CustomAction`, `PlaceholderResolver`): new methods always come with a default |
| `@Since("1.2")` | Added in that version; everything without it exists since 1.0 |
| `@Experimental` | Not covered yet: may change in any minor version while it is tried out |

Using something newer than your `api-version` on older lobbies: check first, and keep working without it.

```java
if (ApiVersion.supports("1.2")) {
    // use what @Since("1.2") added
}
```

Enums can get new constants in a minor version: give every `switch` over a lobby enum a `default` branch.

The build enforces this promise: the API is recorded in
[`lobby-api/api/lobby-api.api`](../lobby-api/api/lobby-api.api), and the tests fail on any change that does not fit
the version (see [below](#for-lobby-maintainers-changing-the-api)).

## Testing and debugging

- `/setup-lobby addons` lists every addon with its state; `/setup-lobby doctor` explains the ones that did not
  start. The console has the full error.
- `/setup-lobby addons reload <id>` stops the addon, reads its jar again and starts it: replace the jar, reload, no
  restart needed.
- `context.logger()` writes to the lobby's console and log, marked with the addon's name.
- An addon cannot load the lobby's own classes (only the API, Minestom, Adventure, SLF4J, Gson, fastutil and the
  addons it depends on), so a `ClassNotFoundException` for a lobby class means the API does not offer that yet:
  [ask for it](#asking-for-more).

### Asking for more

If an addon needs something the API does not have, open an issue saying what the addon wants to do. New extension
points come in minor versions, so existing addons keep working.

## For lobby maintainers: changing the API

`lobby-api` holds interfaces and small value types only; the lobby implements them in
`lobby-server/.../server/addon/` (`AddonContextImpl` and the `Api*` classes). When a feature should be open to
addons:

1. Add what addons need to `lobby-api`: an event in `api.event` (fired with `LobbyEvents.fire`, after checking
   `LobbyEvents.active()` so servers without addons pay nothing), a method on a service, or a new service on
   `AddonContext`. Mark interfaces the lobby implements `@NonExtendable`, and new things `@Since("x.y")`.
2. Implement it in `server/addon/`. Anything an addon registers must be removed in `AddonContextImpl.cleanup()`.
3. Raise the version in `lobby-api/build.gradle.kts` and `ApiVersion.CURRENT`: the next minor version for
   additions; the next major version only for removals or changes (deprecate first).
4. Run `./gradlew :lobby-api:apiDump`. It writes `lobby-api/api/lobby-api.api`, and refuses when the version does
   not fit the change: a removal without a major version, an addition without a minor version or without
   `@Since`. Commit the file with the change.
5. Add it to this guide, and to [upgrading.md](upgrading.md) if server owners need to know.

`ApiCompatibilityTest` fails as long as the API and the file differ, and says what the difference means. Changes to
`@Experimental` parts are free.
