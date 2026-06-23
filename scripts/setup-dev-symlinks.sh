#!/usr/bin/env bash
# The REAL CppBuild.java (and friends) live in CppMode/src/java/. This
# script creates symlinks INSIDE your processing4 checkout (at
# java/src/processing/mode/cpp/) pointing back at these real files, so
# that running Gradle from processing4 compiles the same source you're
# editing here -- no need to keep two copies in sync.
#
# Usage:
#   ./setup-dev-symlinks.sh /path/to/your/processing4

set -e

if [ -z "$1" ]; then
  echo "Usage: $0 /path/to/your/processing4"
  echo "Example: $0 ~/Projects/processing4"
  exit 1
fi

if [ ! -d "$1" ]; then
  echo "ERROR: '$1' is not a directory."
  exit 1
fi

PROCESSING4_DIR="$(cd "$1" && pwd)"
DEST_DIR="$PROCESSING4_DIR/java/src/processing/mode/cpp"

# This script lives in CppMode/scripts/, so CppMode's own root is one
# directory up from wherever this script actually is.
CPPMODE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$CPPMODE_DIR/src/java"

if [ ! -d "$SRC_DIR" ]; then
  echo "ERROR: $SRC_DIR does not exist."
  echo "Expected the real .java source files to live there."
  exit 1
fi

mkdir -p "$DEST_DIR"

echo "real source:  $SRC_DIR"
echo "linking into: $DEST_DIR"
echo

shopt -s nullglob
SOURCE_FILES=("$SRC_DIR"/*.java)
shopt -u nullglob

if [ ${#SOURCE_FILES[@]} -eq 0 ]; then
  echo "WARNING: no .java files found in $SRC_DIR -- nothing to link."
  exit 0
fi

linked=0
skipped=0
warned=0

for f in "${SOURCE_FILES[@]}"; do
  name="$(basename "$f")"
  target="$DEST_DIR/$name"

  if [ ! -e "$f" ]; then
    echo "WARNING: $name disappeared from source -- skipping."
    warned=$((warned+1))
    continue
  fi

  if [ -L "$target" ]; then
    current_link="$(readlink "$target")"
    if [ "$current_link" == "$f" ]; then
      skipped=$((skipped+1))
      continue
    fi
    echo "Replacing stale/incorrect symlink: $name (was -> $current_link)"
    rm "$target"
  elif [ -e "$target" ]; then
    backup="$target.bak"
    n=1
    while [ -e "$backup" ]; do
      backup="$target.bak.$n"
      n=$((n+1))
    done
    echo "Backing up existing $name -> $(basename "$backup")"
    mv "$target" "$backup"
  fi

  ln -s "$f" "$target"
  echo "linked $name"
  linked=$((linked+1))
done

echo
echo "Done: $linked linked, $skipped already correct, $warned warnings."
echo "$DEST_DIR now points at the real source in $SRC_DIR."
echo "Build the jar by running ./gradlew from $PROCESSING4_DIR."
