#!/usr/bin/env bash
# Single, authoritative way to rebuild the CppMode C++ engine (Processing.o).
# Reads the DEBUG file itself -- this script IS the source of truth for
# whether the rebuilt .o has debug output compiled in, replacing the
# previous approach of manually remembering to add/omit -DPROCESSING_DEBUG
# by hand each time (which is exactly what caused today's confusion: a
# .o file silently left over from an earlier manual debug build, with no
# way to tell from the outside whether it matched the DEBUG file or not).
set -e

# This script lives in CppMode/scripts/, so CppMode's own root is one
# directory up from wherever this script actually is.
CPPMODE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$CPPMODE_DIR/src"
CACHE_DIR="$CPPMODE_DIR/cache/linux-x64"
DEBUG_FILE="$CPPMODE_DIR/DEBUG"

mkdir -p "$CACHE_DIR"

DEBUG_VAL="0"
if [ -f "$DEBUG_FILE" ]; then
  DEBUG_VAL="$(cat "$DEBUG_FILE" | tr -d '[:space:]')"
fi

EXTRA_FLAGS=""
BUILD_STAMP="RELEASE"
if [ "$DEBUG_VAL" == "1" ]; then
  EXTRA_FLAGS="-DPROCESSING_DEBUG"
  BUILD_STAMP="DEBUG"
fi

echo "Rebuilding Processing.o -- DEBUG file says '$DEBUG_VAL' -> build stamp: $BUILD_STAMP"

rm -f "$CACHE_DIR/Processing.o"

g++ -std=c++17 -O2 -march=native -c \
    -I"$SRC_DIR" \
    -DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE \
    -DPROCESSING_BUILD_STAMP="\"$BUILD_STAMP\"" \
    $EXTRA_FLAGS \
    "$SRC_DIR/Processing.cpp" \
    -o "$CACHE_DIR/Processing.o"

echo "Done. Built with stamp: $BUILD_STAMP"
ls -la "$CACHE_DIR/Processing.o"
