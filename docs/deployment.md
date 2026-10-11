# Deployment

Ready-made files for running the lobby are in [`deploy/`](../deploy). They all start the same jar with
the same memory settings.

## Hardware

Measured with the 5-minute load test (`LobbyLoadTest`): 200 players walking, 50 holograms, 30 NPCs, the
bundled scoreboard, tab list, nametags and boss bar, hotbar, visibility, pads and portals, on a laptop
**Intel Core i3-8145U (2 cores, 4 threads)** with a **512 MB** heap:

| | |
|---|---|
| Server tick (MSPT) | 1.7 ms on average, 28 ms at most; no tick over 50 ms |
| Garbage collection | about 2% of the time |
| Memory allocated | about 230 MB per second |

So, for one lobby server with 200 players (**estimates**, add room for LuckPerms and a big map):

| | Minimum | Recommended |
|---|---|---|
| Java heap (`MEMORY`) | 1 GB | 2 GB |
| RAM for the machine or container | 1.5 GB | 3 GB |
| CPU | 2 cores | 2-4 cores; single-core speed matters most |
| Disk | 1 GB | SSD; the map is loaded into memory at start, so disk speed only matters for the start |

The load test runs the fake players in the same process, so a real server with real clients needs no more
than this. Run several lobbies (one per 200 players) rather than one huge one: [instances.md](instances.md)
for several lobbies on one server.

Java **25** is required.

## Start scripts

[`deploy/start.sh`](../deploy/start.sh) (Linux, macOS) and [`deploy/start.bat`](../deploy/start.bat)
(Windows): put them next to `lobby-server.jar`.

```bash
chmod +x start.sh
./start.sh                 # 2 GB heap
MEMORY=1G ./start.sh       # a smaller heap
RESTART=1 ./start.sh       # start again after a crash or stop
```

They use G1 with the well-known "Aikar flags" for Minecraft servers (short garbage-collection pauses, a
large young generation for the many short-lived objects) and `-Xms` = `-Xmx` so the heap is reserved up
front.

## systemd (Linux server)

[`deploy/systemd/lobby.service`](../deploy/systemd/lobby.service):

```bash
sudo useradd --system --home /opt/lobby minecraft
sudo mkdir -p /opt/lobby && sudo cp lobby-server.jar start.sh /opt/lobby/ && sudo chown -R minecraft /opt/lobby
sudo cp lobby.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now lobby
journalctl -u lobby -f
```

`systemctl stop lobby` saves everything first, like `stop`. Under systemd there is no console; use
commands in game, or stop the service and run `./start.sh` in `screen`/`tmux` when you need it.

**Check it worked:** `systemctl status lobby` shows "active (running)" and the log ends with
`Ready in ... ms`.

## Pterodactyl or Pelican

1. Admin panel → Nests → Import Egg → [`deploy/pterodactyl/egg-minestom-lobby.json`](../deploy/pterodactyl/egg-minestom-lobby.json).
2. Create a server with that egg, image "Java 25", at least 1.5 GB of memory.
3. Leave "Download URL" empty and upload `lobby-server.jar` with the file manager (or give a URL to a
   release jar).
4. Start it. The panel writes the allocation's port into `config.yml` on every start; the console accepts
   commands, the "stop" button runs `stop`.

The heap is set to 90% of the container's memory (`-XX:MaxRAMPercentage=90.0`), so set the server's memory
in the panel, not in a flag. Behind a proxy, give the lobby an allocation the proxy can reach but players
cannot ([firewall](../examples/network/firewall.md)).

**Check it worked:** the panel turns green ("Ready in" in the log) and `tps` in the console answers.

## Docker

[`deploy/docker/Dockerfile`](../deploy/docker/Dockerfile) and
[`deploy/docker/docker-compose.yml`](../deploy/docker/docker-compose.yml) (with MariaDB):

```bash
./gradlew build
docker compose -f deploy/docker/docker-compose.yml up -d --build
docker compose -f deploy/docker/docker-compose.yml logs -f lobby
```

The server's folder is the `lobby-data` volume (or bind-mount a folder, see the comments in the file).
`docker compose stop lobby` saves first. Change the database passwords in the file before using it.

## Updating

1. Stop the lobby (`stop`, or stop the service/container).
2. Replace `lobby-server.jar` (and `lobby-bridge.jar` on the proxy).
3. Read [upgrading.md](upgrading.md) for anything you need to change.
4. Start it again and run `/setup-lobby doctor`.
