#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/chess.jar"

if [[ ! -f "$JAR" ]]; then
  echo "Building chess.jar…"
  # Redirect sbt stdin from /dev/null so it cannot consume the terminal's stdin.
  # Without this, sbt exhausts stdin and the game sees EOF immediately on launch.
  (cd "$SCRIPT_DIR" && sbt --no-server "ui-text/assembly" </dev/null)
fi

exec java -Dfile.encoding=UTF-8 -jar "$JAR"
