# Contributing

Thanks for helping! This project is meant to be easy to read and easy to run, for both server owners
and developers. Please keep that in mind in every change.

## Ground rules

- **English everywhere**: code, comments, config files, log messages, commit messages and docs.
- **Server owners first**: every new config option needs a short comment above it that says what it
  does and which values are allowed, and a safe default. If an option is not needed yet, do not add it.
- **No player-facing text in code**: put it in `messages.yml` (MiniMessage format) and add a `MessageKey`.
- **Never block the tick thread**: database, HTTP and file work runs on virtual threads
  (`Async.supply` / `DatabasePool.query`), and results go back with `Async.onTickThread`.
- **Optional things stay optional**: every integration sits behind a small interface with a
  "disabled" implementation, and the server must start with all of them turned off.
- Small, focused classes with javadoc on public types. No magic numbers: use named constants.

## Development setup

You need JDK 25 and Git. Clone with `--recursive` (or run `git submodule update --init`): the LuckPerms
Minestom port in `third_party/luckperms` is built from source together with the lobby.

```bash
./gradlew build                  # compile, test, build both jars
./gradlew :lobby-server:run      # start a local server in lobby-server/run/
```

### Database tests

`DatabaseReadersIT` (LiteBans, SkinsRestorer) and `LuckPermsLiveIT` (LuckPerms with SQL messaging) run
against a real MariaDB. They are skipped
unless you set `LOBBY_TEST_DB_PORT` (and optionally `LOBBY_TEST_DB_HOST`, `LOBBY_TEST_DB_NAME`,
`LOBBY_TEST_DB_USER`, `LOBBY_TEST_DB_PASSWORD`). They create and drop their own `lobbytest_` tables and
`lobby_luckperms_it` database. Never point them at a production database.

```bash
LOBBY_TEST_DB_PORT=3306 LOBBY_TEST_DB_PASSWORD=secret ./gradlew test
```

## Commits and pull requests

- One logical change per commit, with a clear message: a short summary line, a blank line, then why.
- Keep `./gradlew build` green.
- Update the docs in `docs/` and the README when behavior or options change.
- If you change the bridge message format, bump `BridgeProtocol.VERSION` and update `docs/bridge-protocol.md`.
