# The lobby in a container. Build from the repository root:
#   docker build -f deploy/docker/Dockerfile -t minestom-lobby .
# By default it downloads lobby-server.jar of the newest release. Another one:
#   --build-arg LOBBY_JAR=https://github.com/mohammadhadimohammadi2007-dot/lobby/releases/download/v0.2.0/lobby-server.jar
# or your own build (after ./gradlew build):
#   --build-arg LOBBY_JAR=lobby-server/build/libs/lobby-server.jar
# Everything the server writes (config, data, logs, worlds) is in /server: mount a volume there.
FROM eclipse-temurin:25-jre

ARG LOBBY_JAR=https://github.com/mohammadhadimohammadi2007-dot/lobby/releases/latest/download/lobby-server.jar

RUN useradd --system --home-dir /server --shell /usr/sbin/nologin minecraft \
    && mkdir -p /server && chown minecraft /server
ADD --chmod=644 ${LOBBY_JAR} /opt/lobby/lobby-server.jar
COPY deploy/start.sh /opt/lobby/start.sh

USER minecraft
WORKDIR /server
EXPOSE 25565
ENV MEMORY=2G JAR=/opt/lobby/lobby-server.jar SERVER_DIR=/server
# start.sh runs java with the recommended flags. "docker stop" sends SIGTERM: the lobby saves and stops.
ENTRYPOINT ["sh", "/opt/lobby/start.sh"]
