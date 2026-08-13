#!/usr/bin/env python3
"""
Creates a clean, distributable .zip of the CppMode folder for a release.

Includes everything in CppMode/ EXCEPT the contents of cache/ subfolders
(cache/linux-x64, cache/windows-x64, etc.) -- those are machine-specific
compiled build artifacts (Processing.o and similar), not something that
belongs in a release archive; they get rebuilt fresh on whatever machine
actually uses the release.

Usage:
    create-release.py [version]

If no version is given, the zip is named CppMode.zip; otherwise it's
named CppMode-<version>.zip (e.g. CppMode-0.2.0.zip).
"""
import os
import sys
import zipfile

# This script lives in CppMode/scripts/, so CppMode's own root is one
# directory up from wherever this script actually is.
CPPMODE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_DIR = os.path.dirname(CPPMODE_DIR)  # one level above CppMode/ itself

def should_skip_file(path):
    """
    Returns True for .o files inside cache/<platform>/, or anything
    inside .git/ or .github/. The cache/<platform>/ DIRECTORIES
    themselves are kept (so the release has the right folder layout
    ready for a fresh rebuild), just not the compiled .o artifacts
    sitting inside them -- those are machine-specific and get rebuilt
    fresh wherever the release is actually used.
    """
    rel = os.path.relpath(path, CPPMODE_DIR)
    parts = rel.split(os.sep)
    if len(parts) >= 1 and parts[0] in (".git", ".github"):
        return True
    if False:  # .o files are included in release (downloaded from CI)
        return True
    return False

def should_prune_dir(path):
    """Directories to not even descend into at all (.git, .github)."""
    rel = os.path.relpath(path, CPPMODE_DIR)
    parts = rel.split(os.sep)
    return len(parts) >= 1 and parts[0] in (".git", ".github")

def bump_version_files(tag):
    """
    Updates mode.properties and CppMode.txt to match the given release
    tag: sets prettyVersion/prettyversion to the tag exactly as given,
    and increments the plain integer "version=" build number by 1 in
    both files. Both files get updated together so they never drift out
    of sync with each other.
    """
    targets = [
        (os.path.join(CPPMODE_DIR, "mode.properties"), "prettyVersion"),
        (os.path.join(CPPMODE_DIR, "CppMode.txt"),      "prettyVersion"),
    ]
    for path, pretty_key in targets:
        if not os.path.exists(path):
            print(f"WARNING: {path} not found, skipping version bump for it.")
            continue
        lines = open(path).readlines()
        new_lines = []
        for line in lines:
            if line.startswith("version="):
                try:
                    current = int(line.strip().split("=", 1)[1])
                except ValueError:
                    current = 0
                new_lines.append(f"version={current + 1}\n")
            elif line.startswith(pretty_key + "="):
                new_lines.append(f"{pretty_key}={tag}\n")
            else:
                new_lines.append(line)
        with open(path, "w") as out:
            out.writelines(new_lines)
        print(f"Updated {path}: {pretty_key}={tag}, version bumped by 1")

def trigger_and_wait_cache_build():
    """Trigger a new build-cache CI run and wait for it to complete."""
    import time
    print("Triggering build-cache CI run...")
    r = subprocess.run(
        ["gh", "workflow", "run", "build-cache.yml", "--repo", repo],
        capture_output=True, text=True
    )
    if r.returncode != 0:
        print(f"  WARNING: Could not trigger build-cache: {r.stderr.strip()}")
        return None
    time.sleep(5)  # wait for run to register
    r2 = subprocess.run(
        ["gh", "run", "list", "--repo", repo, "--workflow", "build-cache.yml",
         "--limit", "1", "--json", "databaseId,status"],
        capture_output=True, text=True
    )
    if r2.returncode != 0: return None
    run_id = json.loads(r2.stdout)[0]["databaseId"]
    print(f"  Waiting for run {run_id} to complete...")
    while True:
        r3 = subprocess.run(
            ["gh", "run", "view", str(run_id), "--repo", repo, "--json", "status,conclusion"],
            capture_output=True, text=True
        )
        data = json.loads(r3.stdout)
        if data["status"] == "completed":
            print(f"  Run {run_id} completed with: {data['conclusion']}")
            return run_id if data["conclusion"] == "success" else None
        print(f"  Status: {data['status']}... waiting 15s")
        time.sleep(15)

def download_cache_artifacts():
    """Download precompiled .o files from latest successful build-cache CI run."""
    import subprocess, json
    from pathlib import Path
    repo = "processing-cpp/processing.cpp"
    root = Path(__file__).parent.parent
    platforms = ["linux-x64", "windows-x64", "macos-arm64", "macos-x64"]
    print("Downloading precompiled cache from CI artifacts...")

    # Find the latest successful build-cache run
    r = subprocess.run(
        ["gh", "run", "list", "--repo", repo, "--workflow", "build-cache.yml",
         "--status", "success", "--limit", "1", "--json", "databaseId"],
        capture_output=True, text=True
    )
    if r.returncode != 0 or not r.stdout.strip():
        print("  WARNING: Could not find successful build-cache run")
        return
    run_id = json.loads(r.stdout)[0]["databaseId"]
    print(f"  Using run {run_id}")

    for platform in platforms:
        dest = root / "cache" / platform
        # Clear existing files so gh run download can overwrite
        import shutil
        if dest.exists(): shutil.rmtree(dest)
        dest.mkdir(parents=True, exist_ok=True)
        result = subprocess.run(
            ["gh", "run", "download", str(run_id), "--repo", repo,
             "--name", f"cache-{platform}", "--dir", str(dest)],
            capture_output=True, text=True
        )
        if result.returncode != 0:
            print(f"  WARNING: {platform}: {result.stderr.strip()[:100]}")
        else:
            files = [f.name for f in dest.iterdir()]
            print(f"  {platform}: {files}")

def main():
    version = sys.argv[1] if len(sys.argv) > 1 else None
    if version:
        bump_version_files(version)
    download_cache_artifacts()
    zip_name = f"CppMode-{version}.zip" if version else "CppMode.zip"
    zip_path = os.path.join(OUTPUT_DIR, zip_name)

    if os.path.exists(zip_path):
        os.remove(zip_path)

    included = 0
    skipped = 0

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(CPPMODE_DIR):
            # Prune .git/.github entirely -- never even descend into
            # them. cache/<platform> directories are walked normally
            # (their FOLDER STRUCTURE is kept), only individual .o
            # files inside them get filtered out below.
            dirs[:] = [
                d for d in dirs
                if not should_prune_dir(os.path.join(root, d))
            ]

            # If this directory ends up with no files going into the
            # zip -- either because it was ALREADY empty (e.g. a fresh
            # cache/windows-x64/ that's never been built into yet) or
            # BECAME empty after filtering out its .o files -- add an
            # explicit directory entry so the folder still exists after
            # extraction. Zip files don't store empty directories
            # unless told to, and this needs to cover both cases, not
            # just the "had files, now has none" case.
            rel_dir = os.path.relpath(root, CPPMODE_DIR)
            kept_any_file_here = any(
                not should_skip_file(os.path.join(root, f)) for f in files
            )
            if not kept_any_file_here:
                dir_arcname = os.path.join("CppMode", rel_dir) + "/"
                zf.write(root, dir_arcname)

            for fname in files:
                full_path = os.path.join(root, fname)
                if should_skip_file(full_path):
                    skipped += 1
                    continue
                # Store paths inside the zip relative to CppMode's own
                # parent, so extracting the zip recreates a top-level
                # "CppMode/" folder rather than dumping files loose.
                arcname = os.path.join(
                    "CppMode", os.path.relpath(full_path, CPPMODE_DIR)
                )
                zf.write(full_path, arcname)
                included += 1

    size_mb = os.path.getsize(zip_path) / (1024 * 1024)
    print(f"Created {zip_path} ({size_mb:.2f} MB)")
    print(f"  {included} files included, {skipped} cache files skipped")

if __name__ == "__main__":
    main()
