# Example addon: click counter

A small addon for the lobby that counts how often each player clicks an NPC. It is meant to be read: every part of
it is commented, and together it uses most of the addon API.

| What | Where |
|---|---|
| `addon.json` (id, main class, API version) | [src/main/resources/addon.json](src/main/resources/addon.json) |
| A config file copied to `addons/example/` on the first start | [src/main/resources/config.yml](src/main/resources/config.yml) |
| The placeholder `%example_clicks%` | `ClickCounterAddon.onLoad` |
| The action `example_reset` (for NPCs, menus, portals, hotbar items) | `ClickCounterAddon.onLoad` |
| Listening to NPC clicks, joins, quits and a region | `ClickCounterAddon.onEnable` |
| An event of its own, and a listener with a priority | [ClickMilestoneEvent.java](src/main/java/com/example/clicks/ClickMilestoneEvent.java) |
| Saving a value per player (database or files, never blocking) | `setClicks`, `load` |
| The command `/clicks` and a menu built in code | `ClickCounterAddon.onEnable` |
| A region that greets players, and messages with the lobby's `<prefix>` | `ClickCounterAddon.onEnable` |
| A service other addons can use | [ClickCounter.java](src/main/java/com/example/clicks/ClickCounter.java) |

The full guide is [docs/addon-development.md](../../docs/addon-development.md).

## Trying it

```bash
./gradlew :example-addon:jar
```

Copy `examples/addon-example/build/libs/example-addon.jar` into the lobby's `addons/` folder and restart it. Click
an NPC ten times, walk to the spawn (the example zone is around it), and type `/clicks` (add `clicks` to a group in
`commands.yml` first, so the command is allowed). Put `%example_clicks%` in a hologram or the scoreboard.

The lobby's tests start this addon in a real lobby (`ExampleAddonTest`), together with a second addon that uses its
service and its event, so it always works with the current API.

## Using it as a template

1. Copy this folder somewhere outside the lobby's repository.
2. Add a `settings.gradle.kts` next to `build.gradle.kts`:

   ```kotlin
   rootProject.name = "my-addon"
   ```

3. In `build.gradle.kts`, replace the `dependencies` block (the lobby's own build gives it `project(":lobby-api")`
   and `libs.minestom`, which do not exist on their own):

   ```kotlin
   repositories {
       mavenCentral()
       mavenLocal() // where `./gradlew :lobby-api:publishToMavenLocal` in the lobby's repository puts the API
   }

   dependencies {
       compileOnly("io.github.mohammadhadimohammadi2007-dot:lobby-api:1.0.0")
       compileOnly("net.minestom:minestom:2026.10.05-26.2") // the Minestom version of your lobby
   }
   ```

   Without the lobby's repository, download `lobby-api.jar` from the lobby's release and use
   `compileOnly(files("libs/lobby-api.jar"))` instead.

4. Rename the package `com.example.clicks`, and change `id`, `name` and `main` in `addon.json`. Keep
   `api-version` at the API version you build against.
5. `./gradlew jar` (or `gradle jar` without a wrapper) builds the addon.
