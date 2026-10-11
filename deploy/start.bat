@echo off
rem Starts the lobby on Windows. Put this next to lobby-server.jar and double-click it.
rem
rem MEMORY is the Java heap. 2G is plenty for 200 players with everything on (see start.sh for the numbers).
rem Set RESTART=1 to start the server again after it stops or crashes.

cd /d "%~dp0"
if "%MEMORY%"=="" set MEMORY=2G
if "%JAR%"=="" set JAR=lobby-server.jar

if not exist "%JAR%" (
    echo Cannot find %JAR% in %CD%. Copy lobby-server.jar here.
    pause
    exit /b 1
)

set FLAGS=-Xms%MEMORY% -Xmx%MEMORY% -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 --enable-native-access=ALL-UNNAMED

:start
java %FLAGS% -jar "%JAR%" %*
if not "%RESTART%"=="1" goto end
echo The server stopped. Starting again in 5 seconds (close this window to quit)...
timeout /t 5 /nobreak >nul
goto start

:end
pause
