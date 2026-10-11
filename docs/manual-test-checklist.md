# Manual test checklist

Things the automated tests cannot prove, because they depend on a real client or on ViaVersion,
ViaBackwards and ViaRewind. Run them before a release on a Velocity network with the Via plugins on the
proxy, with a **1.8.9** client and the **latest** client side by side. Most players of a typical network
are on 1.8, so the 1.8 items come first.

## 0. The client version (check this first)

Everything 1.8-specific below depends on the lobby knowing that a client is 1.8.

- [ ] Join with **1.8.9** through Velocity and run `/setup-lobby info`: "Your client" says
      `1.8.x (protocol 47), legacy, reported by the proxy`. If it says protocol 393 (1.13) or "not reported",
      the 1.8 handling is off for everyone; fix that before testing anything else.
- [ ] Join with the latest client: "Your client" names the latest version, `modern, reported by the proxy`.

## 0b. Console (in a real terminal, then in Pterodactyl or Docker)

- [ ] In a terminal: Tab completes `setup-` to `setup-lobby`, and offers `reload`, `info`, `setspawn` after it.
- [ ] Arrow up shows the last command, also after a restart.
- [ ] A warning prints yellow above the prompt while you are typing; your typed text stays intact.
- [ ] Ctrl+C: "Ctrl+C: stopping the server...", everything saved, the terminal works normally afterwards.
- [ ] In Pterodactyl (or `docker logs`): no color codes like `[38;5;7m` anywhere; commands typed in the
      panel console run.
- [ ] Restart twice: `logs/` has `<today>-1.log.gz` and `<today>-2.log.gz`; `latest.log` holds only the
      current run.

## 0c. Operators (standalone offline mode first)

- [ ] `op <your name>` in the console while you are offline. Join: you are NOT an operator yet, the chat
      tells you to confirm, and the console shows a code. `/op confirm <code>`: you are an operator.
- [ ] Rejoin from the same address: an operator right away. From another address (e.g. mobile hotspot):
      a new code, no rights until confirmed.
- [ ] A friend joins with your name from another address: no rights, and `/op` is the only extra command
      they see.
- [ ] Put `operators: ["YourName"]` in an old config.yml and start: the log says it was moved, config.yml
      has a comment instead, `ops` lists you.
- [ ] Behind Velocity: `op <name>` for someone offline shows "will be an operator when they first join";
      after they join, `data/ops.yml` has their UUID and they are an operator without a code.
- [ ] With LuckPerms: an operator without any LuckPerms group can use `/npc`; after
      `ops.have-all-permissions: false` and `/setup-lobby reload` they cannot.

## 0d. Setup checklist, doctor and wand

- [ ] Fresh folder, standalone: `/setup-lobby setup` shows ✘ Map, ⚠ Operators, ⚠ Connection; in game the
      `[...]` parts are clickable and put the command in the chat box.
- [ ] Behind Velocity with the bridge and the Via plugins: ✔ Proxy forwarding, ✔ Proxy bridge (with the
      groups), ✔ ViaVersion. Remove ViaRewind on the proxy: ViaVersion turns ⚠ "1.8 clients cannot join".
- [ ] Wrong database password in integrations.yml: `/setup-lobby doctor` says what to check (password,
      GRANT); a stopped database: "cannot be reached".
- [ ] Bridge plugin removed from the proxy, a player joins: the doctor lists what to check on the proxy.
- [ ] `/setup-lobby wand`: hit and right-click two blocks; the green outline shows only for you and only
      while you hold the wand. Click an NPC with it: "Selected the NPC ...", its actions do not run.
      Sneak-hit a block inside a portal: "Selected the portal ...". An old portal wand still works.

## 0e. Deployment kit (do the ones you use)

- [ ] `deploy/start.sh` on Linux: starts, `stop` saves, `RESTART=1` starts again after `stop`.
- [ ] systemd: `systemctl stop lobby` shows "Goodbye!" in `journalctl -u lobby`.
- [ ] Pterodactyl/Pelican: import the egg, create a server, upload the jar: it starts, the panel turns green,
      the console takes commands, the stop button saves, the port follows the allocation.
- [ ] Docker: `docker compose -f deploy/docker/docker-compose.yml up -d --build` starts lobby and MariaDB;
      `docker compose stop lobby` saves; the files are in the volume.
- [ ] Follow [setup-guide.md](setup-guide.md) from step 1 to 13 on a fresh machine without other help,
      and note every place where it was unclear or wrong.

## 1. NPC skins on 1.8 (most important)

The lobby lists a player NPC in a 1.8 client's tab list while it appears, then removes it after
`npcs.legacy-tab-removal-delay` (3000 ms by default). Without that, 1.8 clients often show Steve.

- [ ] Create a player NPC with a skin (`/npc create test`, `/npc skin test Notch`). Join on 1.8.9: the
      NPC has the skin, not Steve.
- [ ] Walk out of range (more than 48 blocks) and back: still the skin.
- [ ] Switch lobby (`/lobbies 2`) and back: still the skin.
- [ ] Join with 10+ NPCs in view: every one has its skin.
- [ ] On 1.8, the NPCs show in the tab list for about 3 seconds and then leave it; on the latest
      client they never show.
- [ ] If 1.8 shows Steve on a slow connection, raise `legacy-tab-removal-delay` (e.g. to 5000),
      `/setup-lobby reload`, and check again. Note the value that works.
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
      with their skin after `/lobbies <n>` to the same instance (1.8 included).
- [ ] The boss bar shows and rotates on both (1.8 through ViaRewind).

## 4. Hotbar, selectors and visibility

- [ ] On join and after `/lobbies 2`: compass, nether star, dye and chest in slots 1, 2, 8 and 9, on 1.8
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
- [ ] The portal works in every lobby instance (`/lobbies 2`).

## 6. Network names

- [ ] Behind Velocity with the bridge, rename a group in the bridge's `[groups]` and restart the proxy:
      the lobby logs one warning naming the group, where it is used and the groups the proxy has;
      `/setup-lobby info` shows it under "Network names". Fix it, `/setup-lobby reload`: the line says all names are
      known.

## 7. Holograms on 1.8

- [ ] A hologram with several lines: armor-stand lines on 1.8, text displays on the latest client,
      at the same height.
- [ ] A hologram with `%server_online%` updates on both when someone joins; one with `%player_name%`
      shows each player their own name.
- [ ] Clicking a hologram with actions runs them once (left and right click), on both clients.

## 8. Menus

- [ ] Every bundled menu (lobby menu, game selector, lobby selector) opens on both clients with the right
      items; nothing can be taken, dropped, shift-clicked or swapped with a number key.
- [ ] A menu with `refresh: 20` updates its player counts while open on 1.8.

## 9. Skins and lobby instances

- [ ] `/skin <name>` (SkinsRestorer on the proxy): the new skin shows for you and for the players around
      you without rejoining, on both clients.
- [ ] `/lobbies 2` and back: players, NPCs and holograms of the other instance are gone, then back; your
      own skin and the hotbar stay.

## 10. Startup and mistakes

- [ ] A fresh server folder boots with no warnings, standalone and offline, with every file created.
- [ ] A typo in `display.yml`, `hotbar.yml` or `movement.yml` (e.g. `particle: clowd`) gives one clear
      warning naming the option on `/setup-lobby reload`, and the rest keeps working.
