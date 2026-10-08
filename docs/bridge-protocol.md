# Bridge protocol

The Velocity plugin (`lobby-bridge.jar`) and the lobby servers talk over the plugin message channel
**`lobby:bridge`**. You only need this page if you want to write your own bridge (for example for another
proxy). The reference implementation is `BridgeCodec` in the `lobby-common` module.

Protocol version **1**, message types 1 to 6.

## Directions

| Type | Name | Direction |
|---|---|---|
| 1 | ClientVersion | proxy → lobby |
| 2 | NetworkSnapshot | proxy → lobby |
| 3 | ServerStatus | proxy → lobby |
| 4 | ChatRelay | lobby → proxy → every other lobby |
| 5 | CommandRequest | lobby → proxy |
| 6 | CommandResult | proxy → lobby |

Plugin messages travel through a player's connection. Messages from the lobby to the proxy are sent
through any player on that lobby; a lobby with nobody online can neither send nor receive (harmless:
nobody is there to chat, and fresh data arrives as soon as someone joins).

## Security

A player could try to send a fake `lobby:bridge` message, so:

- the bridge plugin handles **every** `lobby:bridge` message itself and never forwards one;
- it **drops** every message that comes from a player client;
- it only accepts messages from the server connection of a server listed in `lobby-servers`;
- for a chat relay it overwrites the origin with the real server name;
- a `CommandRequest` only runs if the command's first word is in `allowed-commands`, the command has no
  line breaks or other control characters and is at most 256 characters; every command that runs is logged;
- the lobby ignores the channel completely in `standalone` mode (there is no proxy to trust);
- the decoder rejects messages that are too large or malformed.

## Encoding rules

- All numbers are **big-endian**.
- `u8` = unsigned byte, `bool` = byte 0/1, `u16` = unsigned short, `i32` = signed int, `i64` = signed long.
- `uuid` = two `i64`: most significant bits, then least significant bits.
- `string` = `u16` length followed by that many **UTF-8** bytes, max 256 bytes (names, ids).
- `text` = like `string`, max 4096 bytes (chat messages, commands, prefixes).
- `count` = `u16`, max 1024.
- Whole message: max 256 KiB.
- Extra bytes after a known message are an error.

## Header (every message)

| Field    | Type | Value                        |
|----------|------|------------------------------|
| version  | u8   | `1`                          |
| type     | u8   | message type id              |

A reader rejects a message whose version it does not know. A reader **ignores** a message whose type it
does not know, so new message types can be added without a new protocol version. The version only changes
when the layout of an existing type changes.

## Type 1 – ClientVersion

Sent when a player connects to a lobby: the real protocol version of the player's client, which the
lobby cannot see itself when ViaVersion translates the connection.

| Field           | Type | Notes                               |
|-----------------|------|-------------------------------------|
| player          | uuid |                                     |
| protocolVersion | i32  | e.g. `47` = 1.8.x, `762` = 1.19.4   |

## Type 2 – NetworkSnapshot

Sent every `update-interval` seconds (default 2) to every lobby that has players, and once right after a
player joins a lobby.

| Field        | Type   | Notes                                     |
|--------------|--------|-------------------------------------------|
| totalOnline  | i32    | players on the whole proxy                |
| serverCount  | count  | number of entries that follow             |
| ↳ name       | string | backend server name                       |
| ↳ online     | i32    | players on that server                    |
| groupCount   | count  | number of groups that follow              |
| ↳ name       | string | group name, e.g. `bedwars`                |
| ↳ size       | count  | number of servers in the group            |
| ↳↳ server    | string | server name                               |

The group `lobbies` (all `lobby-servers`) is always present.

## Type 3 – ServerStatus

Sent together with every snapshot. The proxy pings every backend server every `status-interval` seconds
(default 10, 3 second timeout).

| Field       | Type   | Notes                                 |
|-------------|--------|---------------------------------------|
| count       | count  |                                       |
| ↳ name      | string | server name                           |
| ↳ online    | bool   | answered the last ping                |
| ↳ maxPlayers| i32    | player limit it reported, 0 if offline|

## Type 4 – ChatRelay

A message in a network-wide chat channel (`global`, `staff`). The sending lobby has already applied its
filters; the text is exactly what players should see. The proxy forwards it to every other lobby in
`lobby-servers` that has players. Receiving lobbies render it with their own formats.

| Field        | Type   | Notes                                             |
|--------------|--------|---------------------------------------------------|
| messageId    | string | short id, the same on every lobby                 |
| channel      | string | e.g. `global`                                     |
| originServer | string | set by the proxy to the real sending server       |
| sender       | uuid   |                                                   |
| senderName   | string |                                                   |
| prefix       | text   | rank prefix (trusted, may contain colors)         |
| suffix       | text   | rank suffix                                       |
| primaryGroup | string | used to pick the chat format                      |
| metaCount    | count  |                                                   |
| ↳ key        | string | rank meta key                                     |
| ↳ value      | text   | rank meta value                                   |
| message      | text   | the chat message                                  |

## Type 5 – CommandRequest

The lobby asks the proxy to run a console command, for example a LiteBans mute after repeated chat
violations. See [Security](#security) for when the proxy accepts it.

| Field     | Type   | Notes                                   |
|-----------|--------|-----------------------------------------|
| requestId | string | echoed in the result                    |
| command   | text   | without `/`, e.g. `tempmute Steve 10m Spam` |
| reason    | string | for the proxy log, e.g. `chat auto-mute` |

## Type 6 – CommandResult

| Field     | Type   | Notes                                  |
|-----------|--------|----------------------------------------|
| requestId | string | from the request                       |
| accepted  | bool   | true if the command ran successfully   |
| detail    | text   | why it was refused or failed, or empty |

## Compatibility

- Phase 1 lobbies (types 1-2 only) reject the new types with a warning in their log; update the lobby and
  the bridge together.
- From Phase 2 on, unknown types are ignored, so a newer bridge works with an older lobby and the other way round.
