# Manual test checklist

Things the automated tests cannot prove, because they depend on a real client or on ViaVersion,
ViaBackwards and ViaRewind. Run them before a release on a Velocity network with the Via plugins on the
proxy, with a **1.8.9** client and the **latest** client side by side. Most players of a typical network
are on 1.8, so the 1.8 items come first.

## 1. NPC skins on 1.8 (most important)

The lobby lists a player NPC in a 1.8 client's tab list while it appears, then removes it after
`npcs.legacy-tab-removal-delay` (3000 ms by default). Without that, 1.8 clients often show Steve.

- [ ] Create a player NPC with a skin (`/npc create test`, `/npc skin test Notch`). Join on 1.8.9: the
      NPC has the skin, not Steve.
- [ ] Walk out of range (more than 48 blocks) and back: still the skin.
- [ ] Switch lobby (`/lobby 2`) and back: still the skin.
- [ ] Join with 10+ NPCs in view: every one has its skin.
- [ ] On 1.8, the NPCs show in the tab list for about 3 seconds and then leave it; on the latest
      client they never show.
- [ ] If 1.8 shows Steve on a slow connection, raise `legacy-tab-removal-delay` (e.g. to 5000),
      `/lobby reload`, and check again. Note the value that works.
- [ ] `/npc skin test Jeb_` while a 1.8 player looks at it: the skin changes without rejoining.
- [ ] `/npc show_in_tab test true`: the NPC stays in the tab list on both clients.

## 2. Other NPC looks

- [ ] `/npc glowing test red`: a red outline on the latest client (1.8 has no glowing).
- [ ] `/npc scale test 2`: twice as big on the latest client, normal size on 1.8; on both, the name is
      right above the head.
- [ ] `/npc pose test crouching`, `sleeping`, `swimming`: drawn so on the latest client.
- [ ] `/npc pose test sitting`: sits at its position on the latest client and on 1.8 (it rides an
      invisible seat). Note how far it floats or sinks on each.
- [ ] A villager NPC with `/npc glowing trader blue`: a blue outline.

## 3. Scoreboard and tab list

- [ ] 1.8.9: the sidebar shows every line without cut-off colours; a long line ends cleanly at 32
      characters; the red numbers are visible (they cannot be hidden before 1.20.3).
- [ ] Latest client: no red numbers; the animated title changes.
- [ ] Tab list on both: ranks with the highest LuckPerms weight on top; header and footer show.
- [ ] Nametags above heads show the rank prefix on both clients (cut to 16 characters on 1.8).
- [ ] With `tab.show: instance`, players of another lobby instance leave the tab list, and come back
      with their skin after `/lobby <n>` to the same instance (1.8 included).
- [ ] The boss bar shows and rotates on both (1.8 through ViaRewind).

## 4. Hotbar, selectors and visibility

- [ ] On join and after `/lobby 2`: compass, nether star, dye and chest in slots 1, 2, 8 and 9, on 1.8
      and on the latest client.
- [ ] They cannot be moved, dropped (Q), swapped to the off hand (F) or placed, on either client.
- [ ] Right-clicking each one in the air and on a block runs it exactly once (1.8 through ViaRewind sends
      block clicks differently).
- [ ] The game selector: a game shows "Click to play", "Full" or "Offline" as its servers are; a click
      connects through the proxy.
- [ ] The lobby selector: this server's lobbies, then the network's other lobby servers; a click
      connects.
- [ ] The visibility switch: all, then staff only, then nobody; NPCs and holograms stay. Rejoin: the
      choice is kept.

## 5. Movement and portals

- [ ] Double jump in adventure mode: jump, then jump again in the air; on 1.8 and on the latest client. It
      does not work again before landing, and not at all in creative or with `/fly` on.
- [ ] `/fly` with and without `lobby.fly`.
- [ ] A light weighted pressure plate on a slime block throws you forward; a heavy one straight up.
- [ ] `/portal wand`, hit one corner, right-click the other, `/portal create test`,
      `/portal action test add connect_group: <a real group>`: walking in connects you. With a group whose
      servers are all down, you are put back outside and pushed away, and told why.
- [ ] The portal works in every lobby instance (`/lobby 2`).

## 6. Network names

- [ ] Behind Velocity with the bridge, rename a group in the bridge's `[groups]` and restart the proxy:
      the lobby logs one warning naming the group, where it is used and the groups the proxy has;
      `/lobby info` shows it under "Network names". Fix it, `/lobby reload`: the line says all names are
      known.

## 7. Holograms on 1.8

- [ ] A hologram with several lines: armor-stand lines on 1.8, text displays on the latest client,
      at the same height.
- [ ] Persian text reads right to left on both.
- [ ] A hologram with `%server_online%` updates on both when someone joins; one with `%player_name%`
      shows each player their own name.
- [ ] Clicking a hologram with actions runs them once (left and right click), on both clients.

## 8. Menus

- [ ] Every bundled menu (`/lobby`, game selector, lobby selector) opens on both clients with the right
      items; nothing can be taken, dropped, shift-clicked or swapped with a number key.
- [ ] A menu with `refresh: 20` updates its player counts while open on 1.8.

## 9. Skins and lobby instances

- [ ] `/skin <name>` (SkinsRestorer on the proxy): the new skin shows for you and for the players around
      you without rejoining, on both clients.
- [ ] `/lobby 2` and back: players, NPCs and holograms of the other instance are gone, then back; your
      own skin and the hotbar stay.

## 10. Startup and mistakes

- [ ] A fresh server folder boots with no warnings, standalone and offline, with every file created.
- [ ] A typo in `display.yml`, `hotbar.yml` or `movement.yml` (e.g. `particle: clowd`) gives one clear
      warning naming the option on `/lobby reload`, and the rest keeps working.
