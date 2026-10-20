#!/bin/sh
# Builds launcher/VibeLauncher.jar on Linux and macOS. Pass --skip-tests to package without testing.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"
OUTPUT="$ROOT/VibeLauncher.jar"

if ! command -v javac >/dev/null 2>&1; then
    echo "[ERROR] A JDK 11 or newer is required to build the launcher (JDK 21 recommended)." >&2
    exit 1
fi

rm -rf "$BUILD"
mkdir -p "$CLASSES" "$TEST_CLASSES"

# -sourcepath lets javac find every class from the entry point, without file lists.
echo "[Vibe Launcher] Compiling Java 8-compatible sources..."
javac --release 8 -encoding UTF-8 -Xlint:-options -sourcepath "$ROOT/src" -d "$CLASSES" "$ROOT/src/dev/vibe/launcher/VibeLauncher.java"

if [ "${1:-}" != "--skip-tests" ]; then
    echo "[Vibe Launcher] Running tests..."
    javac --release 8 -encoding UTF-8 -Xlint:-options -cp "$CLASSES" -sourcepath "$ROOT/test" -d "$TEST_CLASSES" "$ROOT/test/dev/vibe/launcher/LauncherTests.java"
    if ! java -Djava.awt.headless=true -cp "$CLASSES:$TEST_CLASSES" dev.vibe.launcher.LauncherTests; then
        echo "[ERROR] Launcher tests failed; the JAR was not replaced." >&2
        exit 1
    fi
fi

echo "[Vibe Launcher] Packaging executable JAR..."
VERSION=$(sed -n 's/.*String VERSION = "\([^"]*\)".*/\1/p' "$ROOT/src/dev/vibe/launcher/VibeLauncher.java")
printf 'Main-Class: dev.vibe.launcher.VibeLauncher\nImplementation-Title: Vibe Launcher\nImplementation-Version: %s\n' "$VERSION" > "$BUILD/manifest.txt"
jar cfm "$BUILD/VibeLauncher.staged.jar" "$BUILD/manifest.txt" -C "$CLASSES" .
mv -f "$BUILD/VibeLauncher.staged.jar" "$OUTPUT"

# Release asset pair for the launcher's self-update: VibeLauncher.jar + VibeLauncher.jar.sha256
if command -v sha256sum >/dev/null 2>&1; then HASH=$(sha256sum "$OUTPUT" | cut -d' ' -f1); else HASH=$(shasum -a 256 "$OUTPUT" | cut -d' ' -f1); fi
printf '%s  VibeLauncher.jar\n' "$HASH" > "$BUILD/VibeLauncher.jar.sha256"

echo
echo "Build complete: $OUTPUT"
echo "SHA-256:        $HASH"
echo "Run it with:    java -jar \"$OUTPUT\""
