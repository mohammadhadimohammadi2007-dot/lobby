# Third-party notices

This project is [MIT](LICENSE) licensed. It uses the third-party work listed here. Nothing in this
repository is copied from a GPL-licensed project.

## Data in this repository

### PrismarineJS/minecraft-data — MIT

<https://github.com/PrismarineJS/minecraft-data>

Every entry of the generated 1.8 compatibility lists comes from this data, through
[`tools/generate-1-8-list.py`](tools/generate-1-8-list.py):

| File | What it holds |
|---|---|
| `lobby-server/src/main/resources/compat/minecraft-1-8-materials.txt` | Items that exist in 1.8 |
| `lobby-server/src/main/resources/compat/minecraft-1-8-entities.txt` | Entity types that exist in 1.8 |
| `lobby-server/src/test/resources/compat/1-8-source.json` | The slice of the source data the test rebuilds the item list from |

The sources used are `data/pc/1.8/items.json`, `data/pc/1.8/entities.json`,
`data/pc/common/legacy.json` and the item and entity lists of the version the lobby runs on. The
protocol version names in `lobby-common/.../ProtocolVersions.java` come from the same project's
`data/pc/common/protocolVersions.json`.

## Code in this repository

### LuckPerms — MIT

<https://github.com/LuckPerms/LuckPerms>

`third_party/luckperms` is a git submodule pointing at a fork of LuckPerms that adds the Minestom
platform. It is not part of this project's source and is not committed here; it is checked out and
built only when you want the permissions plugin. See
[`docs/integrations/luckperms.md`](docs/integrations/luckperms.md).

## Libraries the lobby and the bridge use

Shipped inside `lobby-server-all.jar` (the `implementation` dependencies):

| Library | License |
|---|---|
| [Minestom](https://github.com/Minestom/Minestom) | Apache-2.0 |
| [Polar](https://github.com/hollow-cube/polar) | MIT |
| [Adventure](https://github.com/KyoriPowered/adventure) (MiniMessage) | MIT |
| [Configurate](https://github.com/SpongePowered/Configurate) | Apache-2.0 |
| [SLF4J](https://www.slf4j.org/) | MIT |
| [Logback](https://logback.qos.ch/) | EPL-1.0 or LGPL-2.1 (dual) |
| [HikariCP](https://github.com/brettwooldridge/HikariCP) | Apache-2.0 |
| [MariaDB Connector/J](https://github.com/mariadb-corporation/mariadb-connector-j) | LGPL-2.1-or-later |
| [Gson](https://github.com/google/gson) | Apache-2.0 |
| [fastutil](https://fastutil.di.unimi.it/) | Apache-2.0 (compile-only; used if Minestom brings it) |
| [JUnit 5](https://junit.org/) | EPL-2.0 (tests only) |

### Compile-only APIs of GPL-3.0 programs

The Velocity bridge plugin is built against two APIs that belong to GPL-3.0 programs:

| API | License | Why |
|---|---|---|
| [velocity-api](https://github.com/PaperMC/Velocity) | GPL-3.0 | Any Velocity plugin must be compiled against it |
| [viaversion-api](https://github.com/ViaVersion/ViaVersion) | GPL-3.0 | Optional: asks ViaVersion for a player's real client version, with a fallback when it is absent |

Both are `compileOnly`: no ViaVersion or Velocity code is copied into this repository and neither is
bundled in any jar this project produces. They are provided at runtime by the proxy the server owner
installed.
