@echo off
setlocal

set "GAME_JAR=%~dp0..\魔绫地牢-V0.9.6.5.jar"
set "AGENT_JAR=%~dp0dist\myriad-codex-agent-0.1.0-SNAPSHOT.jar"

if not exist "%GAME_JAR%" (
  echo Game JAR not found: "%GAME_JAR%"
  pause
  exit /b 1
)

if not exist "%AGENT_JAR%" (
  echo External agent JAR not found: "%AGENT_JAR%"
  pause
  exit /b 1
)

java -javaagent:"%AGENT_JAR%" -jar "%GAME_JAR%" %*
