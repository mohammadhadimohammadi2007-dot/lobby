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

## 4. Holograms on 1.8

- [ ] A hologram with several lines: armor-stand lines on 1.8, text displays on the latest client,
      at the same height.
- [ ] Persian text reads right to left on both.
