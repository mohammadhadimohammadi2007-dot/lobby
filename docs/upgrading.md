# Upgrading

Changes that need something from you when you update an existing lobby. Newest first.

## Phase 4

- **Addon API 1.0** ([addon-development.md](addon-development.md)): the API is final and has a compatibility
  promise: addons built for 1.x run on every later 1.y. Addons made for 0.3 must be rebuilt with
  `api-version: "1.0"` (nothing they used changed). New: services addons offer each other (`services()`), events
  of their own (`events().fire`), listener priorities, `texts()` for messages with the lobby's prefix, and
  `regions()` with regions of their own. The example addon is in `examples/addon-example`. Nothing changes for
  servers without addons.
- **More cosmetics** ([cosmetics.md](cosmetics.md)): outfits, pets and balloons, the trail styles `ring`, `wings`
  and `cloud`, and the gadgets `paintball`, `grapple`, `confetti`, `tnt` and `lightning`. An existing
  `cosmetics.yml` is not changed: copy what you want from the new default (delete the file to get it), or add your
  own. The cosmetics menu now has 4 rows. New placeholders `%cosmetics_pet%`, `%cosmetics_balloon%`,
  `%cosmetics_outfit%`; new messages `cosmetics-pets`, `cosmetics-balloons`, `cosmetics-outfits` and
  `cosmetics-gadget-no-target`. Outfit pieces are tagged and locked like the hat; a PvP kit's armor still replaces
  them in the PvP area. The Addon API's `Cosmetics.Kind` gains `PET`, `BALLOON` and `OUTFIT`.
- **Vanish** ([vanish.md](vanish.md)): `/vanish` for staff. Give your staff group `lobby.command.vanish` and
  `lobby.vanish.see` with LuckPerms (operators have them). The settings table `<prefix>chat_settings` gets a
  `vanished` column automatically. `%server_online%`, `%lobby_online%`, the server list and `/list` no longer count
  vanished players. New messages `vanish-*`; the Addon API 0.3.0 also has `vanish()` and `VanishChangeEvent`.
- **`ops.protection: off`** written without quotes works now (YAML read it as `false`, which was rejected and
  left protection on).
- **Cosmetics** ([cosmetics.md](cosmetics.md)): a new `cosmetics.yml` is created on the first start. Your
  existing `hotbar.yml` is not changed, so add the two new items yourself (the menu item with
  `open_menu: cosmetics`, and an item with `type: gadget`; see [cosmetics.md](cosmetics.md#the-hotbar)), or
  delete `hotbar.yml` to get the new default. Add `cosmetics` to the default group in `commands.yml` so players
  can type `/cosmetics`, and with LuckPerms give the default group `lobby.command.cosmetics`. The settings table
  `<prefix>chat_settings` gets a `cosmetics` column automatically. New placeholders `%cosmetics_...%`, new
  messages `cosmetics-*`. In `hotbar.yml`, an unknown `type:` is now reported and the item left out (before, it
  became a normal item).
- **Addon API 0.3.0**: adds `cosmetics()`, `CosmeticChangeEvent` and `GadgetUseEvent`. Addons made for 0.2 must be
  rebuilt with `api-version: "0.3"`.

- **PvP area** ([pvp.md](pvp.md)): a new `pvp.yml` is created on the first start; nothing changes until an
  area is set with `/pvp setregion`. Update `lobby-bridge.jar` to **0.6.0** so `/server` is blocked during a
  fight. New placeholders `%pvp_...%`, new messages `pvp-*`, a new table `<prefix>pvp_stats` (made
  automatically) or `data/pvp-stats.json`.
- **Addon API 0.2.0**: adds the PvP events and `pvp()`. Addons made for 0.1 must be rebuilt (while the API is
  0.x, the minor version must match).

- **`player_command` actions with a command the lobby does not have now run on the proxy**, as the player
  (for example `player_command: server bedwars`, which used to answer "unknown command"). New action
  `proxy_command: <command>` for commands both have. This needs **lobby-bridge 0.5.0** on the proxy: update
  `lobby-bridge.jar`. Its `config.toml` gets the option `player-commands` (default `true`; add it by hand to
  turn it off). New messages: `proxy-command-not-available`, `proxy-command-unknown`, `proxy-command-failed`.
  See [npcs.md](npcs.md#sending-players-to-servers-and-proxy-commands).

- **Addons.** The lobby now reads jars from an `addons/` folder (created on the first start) and lists them
  with `/setup-lobby addons`; see [addons.md](addons.md). Nothing changes while the folder is empty. Start
  scripts and Docker setups that copy the server folder should keep `addons/` (it also holds the addons'
  own files).

- **The portal wand is now the setup wand** (`/setup-lobby wand`, `/portal wand` still works, and old wands
  in inventories keep working). The messages `portal-wand-*` and `portal-position-set` are replaced by
  `wand-*`.
- **The whole map is preloaded** (`world.preload-whole-map: true`, `world.preload-max-chunks: 20000`), also
  for Anvil folders that are not converted. Startup takes a little longer on big maps and memory use is
  in the log. `preload-radius` now only matters for maps over the limit, or with `preload-whole-map: false`.
- **New server commands:** `list`, `say`, `tp`, `gamemode`, `clear`, `version`, `tps` (see
  [console-and-ops.md](console-and-ops.md)). `/fly <player>` needs `lobby.command.fly.others`. Admin
  commands that used your position also take coordinates (for the console). The messages `npc-created`,
  `hologram-created` and `hologram-moved` no longer say "where you are standing".
- **Operators are stored by UUID in `data/ops.yml`.** The `operators:` list of config.yml is moved there on
  the first start (the log says what was moved) and replaced by a comment. Manage operators with `/op`,
  `/deop` and `/ops`. In standalone offline mode an operator has to confirm with a one-time code from the
  console (`ops.protection: code-and-ip`); see [console-and-ops.md](console-and-ops.md). With LuckPerms,
  operators now have every permission unless `ops.have-all-permissions: false`.
- **Standalone offline mode: players get the vanilla offline UUID** (`OfflinePlayer:<name>`) instead of the
  UUID their client sends, which a client could fake. Settings saved under an old UUID (chat toggle,
  ignore list, visibility) start fresh for players whose client sent a different one.
- **New console.** Tab completion, history and colors (`console.colors`), Ctrl+C stops cleanly. `stop` is
  now a normal command (permission `lobby.command.stop`). Old log archives were named
  `logs/<date>.log.gz`; new ones are `logs/<date>-<n>.log.gz`, made at every start and at midnight.
  `logging.keep-days` (default 14) applies to both. See [console-and-ops.md](console-and-ops.md).
- **New `/announce <message>`** (permission `lobby.command.announce`, click and hover with
  `lobby.command.announce.advanced`) replaces the old `announce` channel. Its look is `announce` in
  messages.yml, its sound and title are `announce:` in chat.yml.
- **One chat, no channels, no private messages.** `channels:`, `default-channel:` and
  `private-messages-enabled:` in chat.yml, `/ch`, the `!`/`#` prefixes and `/msg`/`/r` are gone; chat
  between lobbies and private messages belong to proxy plugins. New option `instance-only` (default
  `true`) keeps chat inside the sender's lobby instance, like the old `local` channel. A message starting
  with `!` or `#` is now sent as written. The `channel` column of `lobby_chat_settings` is left unused and
  new chat log rows have the channel `chat`. Update the bridge plugin on the proxy too: it no longer
  forwards chat between lobbies.
- **Persian/Arabic display fixing is removed** (`/chat persian`, `persian:` in chat.yml, the
  `persian-on`/`persian-off` messages). Texts are shown exactly as written, and every text is rendered
  once per client generation instead of twice. The chat filter still understands Persian and Arabic
  spellings. The old
  `persian:` section and messages are ignored; the `persian` column of `lobby_chat_settings` is left unused.
- **New `commands.yml`.** Players now only see and may type the commands listed for their groups. The
  bundled file lists `/spawn`, `/lobbies`, `/chat` and `/ignore` for everyone. Staff who are not
  operators and have no `lobby.commands.bypass` need a group section, for example
  `mod: { commands: [slowmode, fly] }`. See [commands.md](commands.md). `enabled: false` turns it off.
- **`/lobby` is gone from the lobby server.** On most networks `/lobby` is the proxy's hub command, and the
  two collided.
  - The admin command is now `/setup-lobby` (`reload`, `info`, `setspawn`, ...). The permissions are
    unchanged (`lobby.command.reload`, `lobby.command.info`, `lobby.command.setspawn`).
  - Moving between the lobby instances of this server is `/lobbies <number>`; `/lobbies` alone opens the
    selector. The permission is now `lobby.command.lobbies` (was `lobby.command.lobby`): with LuckPerms,
    give the new one to your default group.
  - Actions that ran `console_command: lobby ...` must use `setup-lobby ...`.
  - In `messages.yml`, `lobby-usage` is now `setup-lobby-usage`. An old file keeps working: missing keys
    use the bundled text.
