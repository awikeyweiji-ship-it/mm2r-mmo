#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/tools/generate_android_stubs.py"
mkdir -p "$ROOT/.test-output/android-stubs"
mapfile -t STUBS < <(find "$ROOT/.test-stubs" -name '*.java' -type f)
mapfile -t SOURCES < <(find "$ROOT/app/src/main/java" -name '*.java' -type f)
javac -Xlint:all,-deprecation,-serial -encoding UTF-8 -d "$ROOT/.test-output/android-stubs" "${STUBS[@]}" "${SOURCES[@]}"
echo "PASS: Android Java sources compile against syntax-only stubs (NOT a platform build)"
