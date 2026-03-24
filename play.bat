@echo off
setlocal

:: UTF-8 so chess glyphs render correctly
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%chess.jar"

if not exist "%JAR%" (
  echo Building chess.jar...
  cd /d "%SCRIPT_DIR%"
  :: Redirect sbt stdin from nul so it cannot consume the console's stdin.
  :: Without this, sbt exhausts stdin and the game sees EOF immediately on launch.
  sbt --no-server "ui-text/assembly" < nul
  if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
  )
)

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%JAR%"
pause
endlocal
