# Maps: Polar, Anvil and conversion

The lobby loads its whole map into memory before players can join and never writes to it while
running. Two formats are supported:

| Format | What it is | `world.path` example |
|---|---|---|
| **World folder** (default) | A normal Minecraft world folder. Converted to Polar automatically. | `worlds/lobby` |
| **Polar** | One compact `.polar` file. Loads fast and uses little memory. | `worlds/lobby.polar` |

## Using a vanilla world

1. Build your lobby in singleplayer or on a Paper server.
2. Copy the **world folder** (the one with `level.dat` inside) next to the lobby jar, e.g. `worlds/lobby/`.
3. Leave the default `world.path: "worlds/lobby"`, or point it at your folder.
   (Older configs with `worlds/lobby.polar` also work: if that file is missing but a `worlds/lobby/`
   world folder exists, the folder is used.)
4. Start the server.

Both world layouts work: the classic one (`region/` in the world folder) and the one used since
Minecraft 26.1 (`dimensions/minecraft/overworld/region/`). Only the overworld is loaded.

## Converting to Polar

With `convert-anvil-to-polar: true` (the default), the first start converts the Anvil world and saves
`worlds/lobby.polar` next to the folder. The original folder is kept. Lighting is computed during the
conversion and stored in the file. Later starts load the `.polar` file directly.

After you edit the world folder, the next start notices that its region files are newer than the
`.polar` file and converts it again. You can also delete the `.polar` file to force a new conversion.
If you only keep the `.polar` file (and delete the folder), it is still found and loaded.

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
