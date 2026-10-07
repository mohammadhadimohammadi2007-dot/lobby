# SkinsRestorer

Reads skins saved by [SkinsRestorer](https://skinsrestorer.net) (version 15 database layout).
**Read only.** SkinsRestorer does not need to be installed on the lobby.

```yaml
skinsrestorer:
  enabled: true
  table-prefix: "sr_"
```

## Do I need this?

- **Behind a proxy:** usually not for players' own skins. SkinsRestorer on the proxy already puts the
  skin into the forwarded profile, and the lobby applies it automatically. Enable it if you want NPCs
  with SkinsRestorer skins (Phase 3).
- **Standalone offline mode:** yes, if you want players' `/skin` choices to show. Without it, offline
  players get the skin of the premium account with the same name (`fetch-skins-for-offline-players`).

## Tables read

| Table | Used for |
|---|---|
| `sr_players` | Which skin each player chose (`skin_identifier`, `skin_type`) |
| `sr_player_skins` | Skins of premium accounts, by UUID |
| `sr_custom_skins` | Skins saved under a custom name |
| `sr_url_skins` | Skins created from an image URL |

## Requirements on the SkinsRestorer side

- SkinsRestorer must use **MySQL** storage (`database.enabled: true` in its config) on the same database.
- `table-prefix` must match `tablePrefix` in its database settings.
