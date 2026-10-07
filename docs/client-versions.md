# Supported client versions

The lobby itself speaks exactly one Minecraft version: the one of the Minestom build it uses
(currently **26.2**). Other versions are handled by ViaVersion **on the proxy**.

| Clients | What you need on the Velocity proxy |
|---|---|
| Same version as the lobby | Nothing |
| Newer versions | ViaVersion |
| Older versions down to 1.9 | ViaVersion + ViaBackwards |
| 1.8.x (e.g. 1.8.9) | ViaVersion + ViaBackwards + ViaRewind |

In standalone mode there is no proxy, so only the lobby's own version can join.

## Old clients

- Keep the map between **Y 0 and Y 255** (see [maps.md](maps.md)).
- Install the [bridge](velocity.md#3-install-the-bridge-optional-recommended) so the lobby knows each
  player's real version. Players below 1.19.4 are marked as `LEGACY`; later phases use this to show
  them compatible holograms, NPCs and menus.
