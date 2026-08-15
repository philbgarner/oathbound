#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGINS_DIR="${OATHBOUND_PLUGINS_DIR:-$HOME/mc-paper-server/plugins}"

if [[ ! -d "$PLUGINS_DIR" ]]; then
    echo "Plugins directory not found: $PLUGINS_DIR" >&2
    exit 1
fi

echo "Building Oathbound..."
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" build

BUILT_JAR="$(find "$PROJECT_DIR/build/libs" -maxdepth 1 -name 'oathbound-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort | tail -n 1)"
if [[ -z "$BUILT_JAR" ]]; then
    echo "No built jar found under $PROJECT_DIR/build/libs" >&2
    exit 1
fi

echo "Removing old Oathbound jar(s) from $PLUGINS_DIR..."
find "$PLUGINS_DIR" -maxdepth 1 -name 'oathbound-*.jar' -print -delete

echo "Copying $(basename "$BUILT_JAR") to $PLUGINS_DIR..."
cp "$BUILT_JAR" "$PLUGINS_DIR/"

echo "Done."
