# Database

LiteBans, SkinsRestorer and LuckPerms all store their data in MySQL/MariaDB. The lobby reads that
same data, so it needs to connect to **the same database your plugins use**.

```yaml
database:
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: ""
  pool-size: 4
```

- The connection is only opened if at least one database integration is enabled.
- At startup the lobby tests the connection. If it fails, the console shows the reason
  (wrong password, server not running...) and every database integration is marked `FAILED`.
  **The lobby still starts.**
- All queries run in the background, never on the server's main thread.
- Each plugin keeps its own table prefix (`luckperms_`, `litebans_`, `sr_`), so all of them can share
  one database. Each integration has its own `table-prefix` option; it must match the plugin's.

## Security

Give the lobby a database user that can **read** the LiteBans and SkinsRestorer tables. The lobby never
writes to them. (LuckPerms needs full access to its own tables because it manages them itself.)
