# Bridge protocol (version 1)

The Velocity plugin (`lobby-bridge.jar`) talks to the lobby server over the plugin message
channel **`lobby:bridge`**. Messages only go **from the proxy to the lobby**.

You only need this page if you want to write your own bridge (for example for another proxy).
The reference implementation is `BridgeCodec` in the `lobby-common` module.

## Security

Plugin messages travel through a player's connection. A player could try to send a fake
`lobby:bridge` message, so:

- the bridge plugin drops every `lobby:bridge` message that comes **from a client**;
- the lobby ignores the channel completely in `standalone` mode (there is no proxy to trust);
- the decoder rejects messages that are too large or malformed.

## Encoding rules

- All numbers are **big-endian**.
- `u8` = unsigned byte, `u16` = unsigned short, `i32` = signed int, `i64` = signed long.
- `string` = `u16` length followed by that many **UTF-8** bytes (max 256 bytes).
- `count` = `u16`, max 1024.
- Whole message: max 256 KiB.
- Extra bytes after a message are an error.

## Header (every message)

| Field    | Type | Value                        |
|----------|------|------------------------------|
| version  | u8   | `1`                          |
| type     | u8   | message type id (see below)  |

A reader must reject a message whose version it does not know. Always update the lobby server
and the bridge plugin together.

## Type 1 – ClientVersion

Sent when a player connects to a lobby. It carries the real protocol version of the player's
client, which the lobby cannot see itself when ViaVersion translates the connection.

| Field            | Type | Notes                               |
|------------------|------|-------------------------------------|
| uuid (high bits) | i64  |                                     |
| uuid (low bits)  | i64  |                                     |
| protocolVersion  | i32  | e.g. `47` = 1.8.x, `762` = 1.19.4   |

## Type 2 – NetworkSnapshot

Sent every few seconds (default 2) to every lobby that has at least one player on it, and once
right after a player joins a lobby.

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

## Known limitation

Plugin messages need a player connection to travel through. A lobby with zero players does not
receive snapshots. That is harmless: nobody is there to see the numbers, and a fresh snapshot is
sent as soon as someone joins.
