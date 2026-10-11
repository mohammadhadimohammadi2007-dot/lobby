# The lobby in a container. Build from the repository root after ./gradlew build:
#   docker build -f deploy/docker/Dockerfile -t minestom-lobby .
# Everything the server writes (config, data, logs, worlds) is in /server: mount a volume there.
FROM eclipse-temurin:25-jre

RUN useradd --system --home-dir /server --shell /usr/sbin/nologin minecraft \
    && mkdir -p /server && chown minecraft /server
COPY lobby-server/build/libs/lobby-server.jar /opt/lobby/lobby-server.jar
COPY deploy/start.sh /opt/lobby/start.sh

USER minecraft
WORKDIR /server
EXPOSE 25565
ENV MEMORY=2G JAR=/opt/lobby/lobby-server.jar SERVER_DIR=/server
# start.sh runs java with the recommended flags. "docker stop" sends SIGTERM: the lobby saves and stops.
ENTRYPOINT ["sh", "/opt/lobby/start.sh"]
