# Vanish

`/vanish` hides a staff member from players, to watch without being seen. Staff with `lobby.vanish.see` still see
them, with `[V]` in front of their name in the tab list.

| Command | Permission | |
|---|---|---|
| `/vanish` (`/v`) | `lobby.command.vanish` | Vanish, or become visible again |
| `/vanish <player>` | `lobby.command.vanish.others` | The same for someone else; also from the console |

| Permission | |
|---|---|
| `lobby.vanish.see` | See vanished players, and have them in `/list` |

Operators have every permission. With LuckPerms, give your staff group the three permissions above (a helper may
only get `lobby.vanish.see`).

**Check it worked:** an operator types `/vanish`: "You are vanished", and a reminder above the hotbar. A second
player without permissions no longer sees them, nor in the tab list, and `%server_online%` is one lower. Another
operator still sees them, with `[V]` in the tab list.

## What a vanished player is hidden from

Everyone without `lobby.vanish.see`:

- in the world (whatever their visibility switch says, also in the PvP area);
- in the tab list;
- in the lobby's counts: `%server_online%`, `%lobby_online%`, `%lobby_online_<n>%`, the lobby selector, the server
  list (count and the names in its hover), and `/list`;
- join and quit messages, and staff join announcements;
- their cosmetics: no trail, no gadget.

They cannot enter the PvP area while vanished (they walk through it without the kit), and someone in the PvP area
cannot vanish until they leave it.

## Rejoining

Vanish is saved with the player's settings (in the shared database if there is one, so it holds on every lobby
server). Someone who leaves while vanished is vanished again on their next join, before anyone can see them:
there is no join message and they never appear. They are told "You are still vanished".

A player who lost `lobby.command.vanish` joins visible.

## What the lobby cannot hide

The proxy knows every player. Behind Velocity:

- the proxy's player count and player list (the network's server list entry, `/glist`, the `%bungee_...%`
  and `%group_online_...%` placeholders) still count a vanished player;
- proxy plugins (friends, `/msg`, staff lists) see them as online, unless they have their own vanish;
- chat: what a vanished player writes is shown as usual.

Use a proxy-side vanish plugin as well if the whole network must not know.

## Placeholders

| Placeholder | Value |
|---|---|
| `%vanish_vanished%` | `yes` or `no`, about the player |
| `%vanish_count%` | how many players are vanished (for a staff scoreboard) |

The texts are the `vanish-*` lines of `messages.yml`; `vanish-tab-marker` is the `[V]` in the tab list (only
staff who see vanished players ever have them in their tab list, so only they see it).

## For addons

`VanishChangeEvent` (cancel it to keep the player as they are) and `context.vanish()`. See [addons.md](addons.md).
