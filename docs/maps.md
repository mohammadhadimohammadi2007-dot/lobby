# Maps: Polar, Anvil and conversion

The lobby loads its whole map into memory before players can join and never writes to it while
running. Two formats are supported:

| Format | What it is | `world.path` example |
|---|---|---|
| **Polar** (recommended) | One compact `.polar` file. Loads fast and uses little memory. | `worlds/lobby.polar` |
| **Anvil** | A normal Minecraft world folder. | `worlds/lobby` |

## Using a vanilla world

1. Build your lobby in singleplayer or on a Paper server.
2. Copy the **world folder** (the one with `level.dat` inside) next to the lobby jar, e.g. `worlds/lobby/`.
3. Set `world.path: "worlds/lobby"` (or leave the default `worlds/lobby.polar`: if that file is missing but
   a `worlds/lobby/` world folder exists, it is used automatically).
4. Start the server.

Both world layouts work: the classic one (`region/` in the world folder) and the one used since
Minecraft 26.1 (`dimensions/minecraft/overworld/region/`). Only the overworld is loaded.

## Converting to Polar

With `convert-anvil-to-polar: true` (the default), the first start converts the Anvil world and saves
`worlds/lobby.polar` next to the folder. The original folder is kept. Lighting is computed during the
conversion and stored in the file. Later starts load the `.polar` file directly.

To convert again (after editing the map), delete the `.polar` file and restart.

With `convert-anvil-to-polar: false`, the Anvil world is read directly: chunks near spawn are
preloaded and the rest load when players walk there.

## Height: keep the lobby between Y 0 and Y 255

Clients older than 1.18 (for example 1.8.9 through ViaRewind) can only see blocks from Y 0 to Y 255.
Build the lobby inside that range. The server warns at startup if blocks exist outside it.

## Spawn and preloading

- `spawn:` is where players appear. Stand somewhere in game and run `/lobby setspawn` to save it.
- `preload-radius` chunks around spawn are always loaded before players can join, even for Anvil.
- Every chunk stored in a Polar file is loaded.

## When the map is missing

If `world.path` does not exist or cannot be read, the server logs why and generates a small grass
platform at spawn, so it never fails to start because of the map.

## Lighting

Stored light is used when present. If the map has no light data, the server computes it once at
startup so the lobby is never dark.
