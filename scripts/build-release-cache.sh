#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

echo "=== Cleaning local cache ==="
rm -rf cache/linux-x64/*.o cache/linux-x64/*.gch
rm -rf cache/windows-x64/*.o cache/windows-x64/*.gch
rm -rf cache/macos-arm64/*.o cache/macos-arm64/*.gch
rm -rf cache/macos-x64/*.o cache/macos-x64/*.gch

echo "=== Triggering CI workflows ==="
gh workflow run build-cache.yml --repo processing-cpp/processing.cpp
gh workflow run build-macos-libs.yml --repo processing-cpp/processing.cpp

echo "=== Waiting for workflows to complete ==="
sleep 15

# Get run IDs
CACHE_RUN=$(gh run list --workflow="build-cache.yml" --repo processing-cpp/processing.cpp --limit 1 --json databaseId -q '.[0].databaseId')
MACOS_RUN=$(gh run list --workflow="build-macos-libs.yml" --repo processing-cpp/processing.cpp --limit 1 --json databaseId -q '.[0].databaseId')

echo "Cache run: $CACHE_RUN"
echo "macOS libs run: $MACOS_RUN"

echo "=== Waiting for cache build... ==="
gh run watch $CACHE_RUN --repo processing-cpp/processing.cpp

echo "=== Waiting for macOS libs build... ==="
gh run watch $MACOS_RUN --repo processing-cpp/processing.cpp

echo "=== Pulling committed artifacts ==="
git pull

echo "=== Done! Ready to run: python3 scripts/create-release.py ==="
