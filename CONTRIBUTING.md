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
- **MIT-compatible sources only**: never copy code or data into this repository from a project under
  GPL, LGPL or another copyleft license, however small the snippet. If the only source of something is
  copyleft, open an issue first. Add anything new to
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **Never hand-write reference data**: lists such as "which items exist in Minecraft 1.8" are generated
  from an upstream source by a script in `tools/`, and the test rebuilds the result from a committed
  slice of that source.

## Development setup

You need JDK 25 and Git. Clone with `--recursive` (or run `git submodule update --init`): the LuckPerms
Minestom port in `third_party/luckperms` is built from source together with the lobby.

```bash
./gradlew build                  # compile, test, build both jars
./gradlew :lobby-server:run      # start a local server in lobby-server/run/
```

### Database tests

`DatabaseReadersIT` (LiteBans, SkinsRestorer), `ChatStorageIT` (chat settings and chat log) and
`LuckPermsLiveIT` (LuckPerms with SQL messaging) run against a real MariaDB. They are skipped
unless you set `LOBBY_TEST_DB_PORT` (and optionally `LOBBY_TEST_DB_HOST`, `LOBBY_TEST_DB_NAME`,
`LOBBY_TEST_DB_USER`, `LOBBY_TEST_DB_PASSWORD`). They create and drop their own `lobbytest_` tables and
`lobby_luckperms_it` database. Never point them at a production database.

CI runs them on every push and pull request against a MariaDB 12.3.2 service container
(`.github/workflows/build.yml`). There `LOBBY_REQUIRE_DB_TESTS=1` is set, and the build fails if any
database test class is skipped or does not run, so a missing or broken database never goes unnoticed.
Locally they simply skip without a database.

```bash
LOBBY_TEST_DB_PORT=3306 LOBBY_TEST_DB_PASSWORD=secret ./gradlew test
```

### Chat load test

`ChatLoadTest` joins 200 fake players who each send 2 messages per second for 60 seconds and reports tick
time, chat thread allocations, renders per message and delivery latency (also written to
`lobby-server/build/reports/chat-load-test.txt`). It takes about two minutes, so it only runs when asked:

```bash
LOBBY_LOAD_TEST=1 ./gradlew :lobby-server:test --tests '*ChatLoadTest'
```

`LOBBY_LOAD_TEST_SECONDS` changes the duration. Run it after changing anything in the chat pipeline.
In CI it runs every Monday and on demand (Actions > Load test > Run workflow), not on every push.

### Lobby load test

`LobbyLoadTest` joins 200 players one per tick, then has them walk in circles for five minutes with 50
holograms, 30 NPCs, the bundled scoreboard, tab list, nametags, boss bar, hotbar, visibility, regions,
pads and portals all running. It reports the tick time (MSPT) per minute, to show it does not grow, the
display thread's load, the allocation rate and GC time (also in
`lobby-server/build/reports/lobby-load-test.txt`):

```bash
LOBBY_LOAD_TEST=1 ./gradlew :lobby-server:test --tests '*LobbyLoadTest'
```

`LOBBY_LOAD_TEST_SECONDS` changes the duration (300 by default). It runs with the chat load test in CI.

### Timings in tests

The regular test suite must give the same answer on every machine. Assert counts (renders, packets,
objects shown after N refreshes, ticks a move was spread over), never milliseconds, and never assume that
work fits in one refresh. Report a timing with `PerfReport.report(what, value, target, unit)`: the
regular build only prints it; with `LOBBY_LOAD_TEST=1` it is also written to
`lobby-server/build/reports/perf.txt`, marked `MISSED` when over its target. The load-test workflow
(weekly and on demand) collects that file and shows it in the run summary; it never fails on a timing.

## Commits and pull requests

- One logical change per commit, with a clear message: a short summary line, a blank line, then why.
- Keep `./gradlew build` green.
- Update the docs in `docs/` and the README when behavior or options change.
- If you change the layout of an existing bridge message, bump `BridgeProtocol.VERSION` and update
  `docs/bridge-protocol.md`. New message types do not need a new version: older readers ignore them.
- Never rewrite history that is already pushed (no amend, rebase or force-push of `main`); fix a mistake
  with a new commit.

## Releasing

Releases are made by the maintainer by pushing a tag; the [Release workflow](.github/workflows/release.yml)
does the rest:

```bash
git tag v0.2.0
git push origin v0.2.0
```

It builds and tests everything (with the database tests), sets the lobby's version to the tag's
(`-PlobbyVersion=0.2.0`, shown by `/version`), and publishes a GitHub Release with `lobby-server.jar`,
`lobby-bridge.jar`, `lobby-api.jar`, `lobby-api-sources.jar` and `SHA256SUMS.txt`, with notes listing the
commits since the previous tag. A tag with a suffix (`v0.3.0-alpha`, `v1.0.0-rc.1`) becomes a pre-release,
which `/releases/latest/download/...` skips. Before tagging: `docs/upgrading.md` is up to date, and the
bridge's `VERSION` and `ApiVersion.CURRENT` are raised if they changed.
