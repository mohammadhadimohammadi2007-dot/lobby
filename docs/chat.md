# Chat

The lobby has its own chat system: rank formats, channels shared between lobbies, a word/link filter that
sees through spelling tricks, anti-spam, mentions, emojis, staff tools, and proper display of **Persian and
Arabic** text, on every client from 1.8.9 to the latest version.

Everything is configured in `chat.yml` (created on first start, every option commented). Texts that players
see (warnings, command replies) are in `messages.yml`. Word lists are in the `filters/` folder.
Apply changes with `/lobby reload`. Only `storage:` and `private-messages-enabled` need a restart.

To turn the whole system off and get plain Minecraft chat, set `enabled: false`.

## How a message travels

Every message goes through the same steps, in order, on one dedicated chat thread (never the server tick
thread, so chat can never lag the lobby):

| Step | What it does | Can |
|---|---|---|
| Mute | LiteBans mute or a local auto-mute | block |
| Cooldown | chat lock, slow mode, per-player message rate | block |
| Anti-spam | new-player checks, length, duplicates, capitals, repeated characters | block or change |
| Normalize | builds the filter's view of the text (see [Filter](#filter)) | - |
| Filter | blocked words, censored words, links, IP addresses | block or change |
| Channel | checks the channel and its permissions | block |
| Format | picks the rank format and finds mentions | - |
| Render | builds the message once per kind of viewer | - |
| Deliver | sends it to everyone who may see it, and to the other lobbies for network channels | - |
| Log | writes it to the chat log | - |

A blocked message is logged too, with the step and the reason. Staff using `/chat spy` see blocked
messages live.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/chat toggle` | `lobby.command.chat` | Hide or show public chat for yourself |
| `/chat persian` | `lobby.command.chat` | Switch the Persian/Arabic display fix for yourself |
| `/chat mentions` | `lobby.command.chat` | Switch mention highlights and sounds for yourself |
| `/ch <channel>` (`/channel`) | `lobby.command.ch` | Switch the channel you write in |
| `/ignore <player>` | `lobby.command.ignore` | Hide a player's messages (again to undo); `/ignore list` |
| `/msg <player> <text>` (`/tell`, `/w`), `/r <text>` | `lobby.command.msg` | Private messages, only if `private-messages-enabled: true` |
| `/chat clear` | `lobby.command.chat.clear` | Clear everyone's chat |
| `/chat lock` | `lobby.command.chat.lock` | Lock or unlock chat (only `lobby.chat.bypass.lock` can talk) |
| `/chat delete <id>` | `lobby.command.chat.delete` | Remove one recent message from everyone's screen |
| `/chat spy` | `lobby.command.chat.spy` | See messages that were blocked, and why |
| `/slowmode <seconds\|off>` | `lobby.command.slowmode` | One message per player every N seconds |

`/chat delete` needs the message id. Staff with `lobby.chat.staff` see it when hovering over a name.
Minecraft cannot remove a single line, so the lobby clears everyone's chat and sends the recent messages
again without the deleted one.

## Permissions

| Permission | Allows |
|---|---|
| `lobby.chat.formatting` | Style messages with the tags in `player-formatting` (and `&` codes for those styles) |
| `lobby.chat.emoji` | `:heart:` style emojis |
| `lobby.chat.mention.everyone` | `@everyone` |
| `lobby.chat.channel.global` | Write in the global channel (default `send-permission`) |
| `lobby.chat.channel.staff` | See and write in the staff channel |
| `lobby.chat.channel.announce` | Write in the announce channel |
| `lobby.chat.format.<name>` | Use the format `<name>` regardless of group |
| `lobby.chat.cooldown.<seconds>` | Own cooldown, e.g. `lobby.chat.cooldown.0` for VIPs (the lowest counts) |
| `lobby.chat.announce.vip` | Join announcement (names are set in `join-announcements`) |
| `lobby.chat.bypass.spam` | Skip every anti-spam check |
| `lobby.chat.bypass.filter` | Skip the filter |
| `lobby.chat.bypass.lock` | Chat while chat is locked |
| `lobby.chat.bypass.slowmode` | Ignore slow mode |
| `lobby.chat.notify` | Filter notifications |
| `lobby.chat.staff` | See message ids in the name hover |

Without LuckPerms, every player has `lobby.command.chat`, `lobby.command.ch`, `lobby.command.ignore`,
`lobby.command.msg` and `lobby.chat.emoji`, and operators have everything.
**With LuckPerms**, give these to your default group:

```
/lp group default permission set lobby.command.chat true
/lp group default permission set lobby.command.ch true
/lp group default permission set lobby.command.ignore true
/lp group default permission set lobby.chat.emoji true
```

## Channels

Four channels come ready: `local` (this lobby only), `global` (every lobby, needs
`lobby.chat.channel.global` to write), `staff` (prefix `#`) and `announce`. Players switch with
`/ch <name>`, or send a single message to a channel by starting it with the channel's prefix: `!hello`
goes to global, `#hello` to staff.

| Option | Meaning |
|---|---|
| `enabled` | Off channels disappear completely |
| `prefix` | One character, or `""` for none. Use a symbol: a message starting with it goes to that channel |
| `permission` | Needed to see and use the channel. `""` = everyone |
| `send-permission` | Also needed to write in it. `""` = nothing extra |
| `network` | `true`: shared with every lobby through the bridge. Without the bridge it stays on this lobby |
| `instance-only` | `true`: only players in the sender's own lobby instance see it. The `local` channel uses this; it only matters with several [lobby instances](instances.md) |
| `format` | How messages look. `<format>` is replaced by the rank format; `<message>`, `<name>` also work |

`default-channel` is where players start. Their last choice is saved.

## Formats

A format decides how a rank's name and message look. The lobby picks the **highest `priority`** format
that matches the sender: their LuckPerms primary group is in `groups`, or they have
`lobby.chat.format.<format name>`. Nothing matches: the format named `default`.

| Option | Meaning |
|---|---|
| `format` | MiniMessage. `<name>`, `<message>`, `<prefix>`, `<suffix>` and any `%placeholder%` about the sender ([list](placeholders.md)) |
| `legacy` | Version for clients older than 1.19.4, which have only 16 colors. `""` = the lobby turns `format` into one by rounding colors |
| `message-color` | Color of the message text, e.g. `<gray>` |
| `hover` | Lines shown when hovering the name |
| `click` | `suggest_command:...`, `run_command:...`, `open_url:...` or `""` |

Prefixes and suffixes from LuckPerms may use `&` codes or MiniMessage.

What players type is **never** read as MiniMessage or placeholders. With `lobby.chat.formatting` they may use
only the styles in `player-formatting` (`color`, `bold`, `italic`, `underlined`, `strikethrough`,
`obfuscated`, `gradient`, `rainbow`). Click, hover, insertion, font, translation, selector, NBT and every
other tag stay visible as plain text. Typing `%server_online%` shows `%server_online%`.

Each message is rendered once per kind of viewer (new or old client, Persian fix on or off, mentioned or
not, staff or not), not once per player, so a message to 200 players costs one or two renders.

## Anti-spam

| Option | Default | Meaning |
|---|---|---|
| `cooldown` | 2 | Seconds per message once `burst` is used up (0-60). `lobby.chat.cooldown.<s>` per rank |
| `burst` | 3 | Messages that may come right after each other |
| `duplicate-check` | true | Block a message that is almost the same as one of the last ones |
| `duplicate-history` | 3 | How many earlier messages are compared |
| `duplicate-similarity` | 85 | Percent alike that counts as the same (compared on the normalized text, so `HELLO!!` = `hello`) |
| `max-caps-percent` | 70 | More capitals than this: the message is made lower case (100 = off) |
| `caps-min-length` | 6 | Capitals check only from this many letters |
| `max-repeated-characters` | 4 | `!!!!!!!!` is shortened to `!!!!` (0 = off) |
| `new-player-delay` | 0 | Seconds after joining before a player may chat |
| `require-move` | true | Players must move once before chatting (stops simple join-and-spam bots) |

`max-length` (top of the file) limits message length. Slow mode (`/slowmode`) and the chat lock
(`/chat lock`) are set by staff and reset on restart.

## Filter

Word lists live in `filters/`:

- `blocked*.txt`: a message containing one of these is not sent (`blocked.txt` has an English starter list,
  `blocked-persian.txt` is empty for you to fill).
- `censored*.txt`: these are replaced by `*****`.
- `allowed*.txt`: exceptions that are never matched (e.g. `scunthorpe`).

One entry per line, `#` starts a comment. `word` matches the whole word only (`ass` does not match `class`
or `as`). `word*` matches words that start with it, `*word` words that end with it, `*word*` anywhere.

Before matching, the lobby builds a normalized copy of the message, so spelling tricks do not help:

- capitals and look-alike letters (Cyrillic `а`, full-width `ｆ`, `𝐟𝐮𝐜𝐤`) → plain letters (Unicode NFKC)
- leetspeak: `sh!t`, `sh1t`, `@ss`, `$hit`
- repeated letters: `fuuuuck` (but the lobby remembers the repeats, so `ass` does not match the word `as`)
- separators: `f u c k`, `f.u.c.k`, `f_u_c_k`
- invisible characters (zero-width spaces and joiners)
- Persian and Arabic: Arabic `ي ك ة` become Persian `ی ک ه`, tatweel `ـ` and diacritics are removed,
  ZWNJ is ignored, Persian and Arabic digits become `0-9`

Censoring always hides the original characters the player typed, at the right position.

Links and IP addresses are detected in their sneaky forms too: `play . badserver . ir`,
`badserver(dot)ir`, `badserver [.] com`, `badserver dot net`, `badserver نقطه ir`, `51 . 89 . 12 . 34`,
`51,89,12,34` and Persian digits. Normal dots (`version 1.8.9`, `e.g.`, `hello.world`) are not links. Domains in `allowed-domains` (and their
subdomains) are allowed. Links cannot be partly hidden, so `censor` blocks them.

For each kind (`blocked-words`, `censored-words`, `links`, `ips`), `actions` is one or more of `block`,
`censor`, `warn` (the player is told), `notify` (staff with `lobby.chat.notify` are told). `points` are
added per offence and go down by `decay-per-minute`.

**Auto-mute** (off by default): at `threshold` points the player is muted for `minutes`. With the bridge the
lobby asks the proxy to run `proxy-command` (for example LiteBans' `tempmute`), so the mute is
network-wide and visible in LiteBans. The first word of that command must be in the bridge's
`allowed-commands`. Without the bridge the player is muted on this lobby only, until restart.

## Persian and Arabic text

Minecraft does not join Persian/Arabic letters and does not show right-to-left text in the right order,
so `سلام` arrives as separate letters, backwards. The lobby fixes this **when showing** a message (never in
what is stored, logged or filtered):

1. letters get their joined forms (initial, middle, final, isolated, and ligatures such as `لا`),
2. the line is put in visual order (bidi), with numbers and English words inside kept left to right.

Every player chooses with `/chat persian`; `persian.default` decides for players who have not chosen.
`persian.server-messages` applies the same fix to messages.yml texts, broadcasts and join messages.

Newer clients **might** already reverse RTL text when the game language is Persian, Arabic or Hebrew. That
has not been confirmed on every version, so the lobby fixes for everyone by default. If players with a
Persian game language see chat backwards, set `trust-rtl-clients: true`: the lobby then skips players
whose client language is RTL (on clients 1.16 and newer).

## Mentions, emojis, ignore, private messages

- **Mentions:** writing a player's name (or `@name`) highlights it for them in `highlight` and plays
  `sound`. `mentions.cooldown` stops one player from pinging the same person over and over. `@everyone`
  needs `lobby.chat.mention.everyone`. Players can turn mentions off with `/chat mentions`.
- **Emojis:** `:heart:` becomes `❤`. Clients older than 1.19.4 get the `legacy` text (`<3`).
- **Ignore:** `/ignore <player>` hides their public messages (and private messages, if those are on).
  Up to 200 players per person.
- **Private messages** are off by default, because most networks handle `/msg` on the proxy. Turn them on
  with `private-messages-enabled: true` (restart). Mutes, ignores and the filter apply to them.

## Join, quit and broadcasts

- `join-message` / `quit-message`: off by default (busy lobbies). Texts are in messages.yml.
- `join-announcements`: rank announcements, e.g. "VIP Steve joined the lobby!". The first entry whose
  permission the player has is used.
- `broadcasts`: messages sent every `interval` minutes, in order or `random`. They may use placeholders
  about the receiving player. `legacy-messages` (same order) are shorter versions for old clients.

## Storage

If the lobby has a database connection (any database integration in `integrations.yml` is on), it creates
two tables with the `storage.table-prefix` (default `lobby_`):

- `lobby_chat_settings`: each player's choices (chat toggle, Persian, mentions, channel, ignore list)
- `lobby_chat_log`: every message, with what the filter did. Written in batches in the background;
  rows older than `log-retention-days` are deleted automatically.

Without a database, settings are JSON files in `data/chat-settings/` and the chat log is daily text files
in `logs/chat/` (`log-to-file`). Nothing ever blocks the chat while writing.

## Proxy or lobby: who handles what?

| Feature | Handled by |
|---|---|
| Chat formats, filter, anti-spam, Persian fix, mentions | The lobby (it sees each player's client version and permissions) |
| Messages between lobbies (`network: true` channels) | The lobby, through the bridge plugin on Velocity |
| `/msg`, `/r` | Usually a proxy plugin (works across all servers); the lobby's own is optional |
| Bans and mutes | LiteBans on the proxy; the lobby reads mutes from the same database |
| Chat of other servers (bedwars...) | Those servers |

Commands handled by a proxy plugin never reach the lobby, so `muted-blocked-commands` only covers lobby
commands. A mute set on the proxy is seen by the lobby within a few seconds (LiteBans integration).

## SignedVelocity (optional)

Since Minecraft 1.19.1 chat messages are signed, and a Velocity plugin can no longer cancel or change them
on the proxy. If a proxy plugin wants to (for example a proxy chat filter or LiteBans on Velocity
cancelling chat of muted players), [SignedVelocity](https://modrinth.com/plugin/signedvelocity) sends that
decision to the backend server, which then applies it.

The lobby understands SignedVelocity's messages without its backend plugin:

1. Install SignedVelocity on your **Velocity** proxy only.
2. In the lobby's `integrations.yml` set `signedvelocity.enabled: true`. It only works with
   `mode: velocity` (otherwise anyone could send fake decisions).
3. Restart the lobby. The startup log shows `SignedVelocity: active`.

Without SignedVelocity (the default), everything works the same, except that proxy plugins cannot cancel or
change chat: the lobby's own mute check (LiteBans) and filter still apply. If the lobby warns that no
SignedVelocity decision arrived, SignedVelocity is not installed on the proxy: turn the option off.

## Performance

Measured with `ChatLoadTest` (200 fake players each sending 2 messages per second for 60 seconds, every
message delivered to all 200 players, on a desktop PC). Run it yourself with
`LOBBY_LOAD_TEST=1 ./gradlew :lobby-server:test --tests '*ChatLoadTest'`.

| Result | Value |
|---|---|
| Messages | 24,040 (401 per second), 4.8 million deliveries |
| Server tick (MSPT) | average 1.56 ms, median 0.80 ms, p99 16 ms (garbage collection of the test JVM) |
| Time from sending to delivered to all 200 players | median 7.5 ms, p99 32 ms |
| Memory allocated by the chat thread | 87 KB per message (about 0.4 KB per player who receives it) |
| Renders per message | 1.1 (one version for everybody, a second one only for mentioned players or staff) |

Chat never runs on the tick thread, so even a flood of messages does not slow down movement or the lobby.
Each version of a message is encoded into a network packet once and the same bytes are sent to everyone
who gets that version.
