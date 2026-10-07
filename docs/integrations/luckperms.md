# LuckPerms

> **Status: not usable yet.** Read [Current problems](#current-problems) before enabling it.
> Until they are solved, the lobby uses the `operators:` list in `config.yml`.

LuckPerms gives the lobby the same ranks, permissions, prefixes and suffixes as the rest of your
network. The lobby runs its own LuckPerms instance against your network's MariaDB, so it reads the
same `luckperms_` tables as LuckPerms on your proxy and Paper servers.

```yaml
luckperms:
  enabled: true
  server-name: "lobby"
  table-prefix: "luckperms_"
  messaging-service: "sql"
```

- All LuckPerms settings come from `integrations.yml`; no separate LuckPerms config file is needed.
- `messaging-service` must match the other LuckPerms configs on your network. Use `sql` so rank
  changes made anywhere apply to the lobby within seconds, even when no player is on it.
- Give your default group `lobby.command.spawn` so players can use `/spawn`.

If LuckPerms is enabled but cannot start, the console shows why, LuckPerms is marked `FAILED`,
and the lobby falls back to the `operators:` list.

## How it is built

LuckPerms has no official Minestom version. The lobby uses the community port at
[LooFifteen/LuckPerms](https://github.com/LooFifteen/LuckPerms/tree/feat/minestom) (branch `feat/minestom`,
artifact `dev.lu15:luckperms-minestom:5.5-SNAPSHOT`).

Because that port is not on Maven Central, LuckPerms support lives in the optional `lobby-luckperms`
module and is only included when building with:

```bash
./gradlew build -PwithLuckPerms
```

The normal build does not need the port at all.

## Current problems

These were found while building this project (October 2026). They have to be fixed in the port, not here.

1. **The port cannot be downloaded.** Its only repository, `repo.hypera.dev`, answers with HTTP 525
   (a broken HTTPS setup on the server).

2. **It targets an older Minestom.** It was written for Minestom `2025.12.20c-1.21.11`.
   Against Minestom `2026.10.05-26.2` it compiles with one change in `minestom/build.gradle`
   (Minestom no longer exposes SLF4J):
   ```groovy
   compileOnly "org.slf4j:slf4j-api:2.0.20"
   ```

3. **It fails at runtime with Adventure 5.** Minestom 26.2 ships Adventure 5, which removed APIs that
   LuckPerms' `common` module still uses:
   - `net.kyori.adventure.translation.TranslationRegistry` → replace with `TranslationStore.messageFormat(key)`
     (in `common/.../locale/TranslationManager.java`).
   - `net.kyori.adventure.util.UTF8ResourceBundleControl` → Java's `ResourceBundle` already reads UTF-8
     `.properties` files since Java 9, so the plain `ResourceBundle.getBundle(name, locale)` works.
   - `TranslatableComponent.Builder.args(...)` → renamed to `arguments(...)`
     (100+ uses in `common/.../locale/Message.java`).

   After these, more Adventure 5 differences may appear; compiling `common` against
   `net.kyori:adventure-api:5.2.0` lists them all.

## Building the port yourself (once it is fixed)

```bash
git clone -b feat/minestom https://github.com/LooFifteen/LuckPerms.git
cd LuckPerms
./gradlew :api:publishToMavenLocal :common:publishToMavenLocal :minestom:publishToMavenLocal -x test -x javadoc
cd ../lobby
./gradlew build -PwithLuckPerms
```

The lobby build looks in your local Maven cache (`~/.m2`) first, then in the port's repository.
