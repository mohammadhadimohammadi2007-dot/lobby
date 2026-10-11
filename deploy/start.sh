#!/usr/bin/env sh
# Starts the lobby. Put this next to lobby-server.jar and run: ./start.sh
#
# MEMORY is the Java heap. 2G is plenty for 200 players with everything on (measured: 200 players walking,
# 50 holograms, 30 NPCs, scoreboard and tab ran with a 512 MB heap and spent 2% of the time in garbage
# collection). Lower it on a small VPS (1G still works), raise it for a huge map.
#
# RESTART=1 starts the server again after it stops or crashes (wait 5 seconds, Ctrl+C twice to quit).
# Under systemd, Docker or Pterodactyl leave RESTART off: they restart the server themselves.
#
# SERVER_DIR is the server folder (config, data, logs, worlds); by default the folder of this script.

MEMORY="${MEMORY:-2G}"
JAR="${JAR:-lobby-server.jar}"
RESTART="${RESTART:-0}"

cd "${SERVER_DIR:-$(dirname "$0")}" || exit 1

if [ ! -f "$JAR" ]; then
    echo "Cannot find $JAR in $(pwd). Copy lobby-server.jar here (or set JAR=...)."
    exit 1
fi

# G1 tuned for a game server that allocates a lot of short-lived objects (about 230 MB/s at 200 players):
# a large young generation, short pauses, and the heap reserved up front. These are the widely used
# "Aikar flags" for Minecraft servers.
FLAGS="-Xms$MEMORY -Xmx$MEMORY \
 -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions \
 -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \
 -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
 --enable-native-access=ALL-UNNAMED"

if [ "$RESTART" != "1" ]; then
    # exec: java replaces this shell, so a stop signal (systemd, docker stop) reaches it and it saves.
    # shellcheck disable=SC2086
    exec java $FLAGS -jar "$JAR" "$@"
fi
while true; do
    # shellcheck disable=SC2086
    java $FLAGS -jar "$JAR" "$@"
    echo "The server stopped. Starting again in 5 seconds (Ctrl+C to quit)..."
    sleep 5
done
