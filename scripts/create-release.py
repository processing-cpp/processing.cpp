#!/usr/bin/env python3
"""
Creates a clean, distributable .zip of the CppMode folder for a release.

All CI workflows are triggered and waited on in parallel using threads.
Artifacts are also downloaded in parallel once their runs complete.

Usage:
    create-release.py [version]

If no version is given, the zip is named CppMode.zip; otherwise it's
named CppMode-<version>.zip (e.g. CppMode-0.2.0.zip).
"""

import os
import sys
import json
import time
import shutil
import zipfile
import tempfile
import subprocess
import threading
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed

REPO         = "processing-cpp/processing.cpp"
CPPMODE_DIR  = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_DIR   = os.path.dirname(CPPMODE_DIR)

# ── Helpers ────────────────────────────────────────────────────────────────────

_print_lock = threading.Lock()

def log(msg):
    with _print_lock:
        print(msg, flush=True)

def gh(*args, **kwargs):
    return subprocess.run(
        ["gh"] + list(args),
        capture_output=True, text=True, **kwargs
    )

# ── Version bump ───────────────────────────────────────────────────────────────

def bump_version_files(tag):
    targets = [
        (os.path.join(CPPMODE_DIR, "mode.properties"), "prettyVersion"),
        (os.path.join(CPPMODE_DIR, "CppMode.txt"),      "prettyVersion"),
    ]
    for path, pretty_key in targets:
        if not os.path.exists(path):
            log(f"WARNING: {path} not found, skipping.")
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
        with open(path, "w") as f:
            f.writelines(new_lines)
        log(f"Updated {os.path.basename(path)}: {pretty_key}={tag}, version bumped")

# ── CI trigger + wait (one workflow) ──────────────────────────────────────────

def trigger_workflow(workflow_file):
    """Trigger a workflow_dispatch run and return its run ID, or None on failure."""
    r = gh("workflow", "run", workflow_file, "--repo", REPO)
    if r.returncode != 0:
        log(f"  [{workflow_file}] trigger FAILED: {r.stderr.strip()}")
        return None
    time.sleep(8)   # give GitHub a moment to register the run
    r2 = gh("run", "list", "--repo", REPO, "--workflow", workflow_file,
             "--limit", "1", "--json", "databaseId")
    if r2.returncode != 0 or not r2.stdout.strip():
        log(f"  [{workflow_file}] could not find run after trigger")
        return None
    run_id = json.loads(r2.stdout)[0]["databaseId"]
    log(f"  [{workflow_file}] triggered → run {run_id}")
    return run_id

def wait_for_run(workflow_file, run_id):
    """Poll until the run completes. Returns True on success."""
    while True:
        r = gh("run", "view", str(run_id), "--repo", REPO,
               "--json", "status,conclusion")
        if r.returncode != 0:
            time.sleep(15)
            continue
        data = json.loads(r.stdout)
        if data["status"] == "completed":
            ok = data["conclusion"] == "success"
            log(f"  [{workflow_file}] run {run_id} → {data['conclusion']}")
            return ok
        log(f"  [{workflow_file}] run {run_id}: {data['status']}… waiting 15s")
        time.sleep(15)

def trigger_and_wait(workflow_file):
    """Trigger a workflow and wait for it. Returns (workflow_file, run_id|None)."""
    run_id = trigger_workflow(workflow_file)
    if run_id is None:
        return workflow_file, None
    ok = wait_for_run(workflow_file, run_id)
    return workflow_file, run_id if ok else None

# ── Artifact downloads ─────────────────────────────────────────────────────────

def download_artifact(run_id, artifact_name, dest_dir, flatten=False):
    """Download a single artifact from a run into dest_dir."""
    dest_dir = Path(dest_dir)
    dest_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        r = gh("run", "download", str(run_id), "--repo", REPO,
               "--name", artifact_name, "--dir", tmp)
        if r.returncode != 0:
            log(f"  WARNING [{artifact_name}]: {r.stderr.strip()[:120]}")
            return
        for f in Path(tmp).rglob("*"):
            if f.is_file():
                shutil.copy2(f, dest_dir / f.name)
        files = [f.name for f in dest_dir.iterdir() if f.is_file()]
        log(f"  {artifact_name}: {files}")

def download_all_artifacts(run_ids):
    """Download all artifacts from completed runs in parallel."""
    root  = Path(CPPMODE_DIR)
    tasks = []

    cache_run = run_ids.get("build-cache.yml")
    if cache_run:
        for platform in ["linux-x64", "windows-x64", "macos-arm64", "macos-x64"]:
            dest = root / "cache" / platform
            if dest.exists():
                shutil.rmtree(dest)
            tasks.append((cache_run, f"cache-{platform}", dest, False))
        # Windows runtime DLLs also live in build-cache artifacts
        tasks.append((cache_run, "windows-runtime-dlls",
                      root / "libs" / "windows-x64", False))

    ts_run = run_ids.get("build-linux-ts-native.yml")
    if ts_run:
        # Download artifact to a staging dir, then distribute files to their destinations
        tasks.append((ts_run, "linux-ts-native",
                      root / "_linux_ts_staging", False))

    log(f"\nDownloading {len(tasks)} artifact(s) in parallel…")
    with ThreadPoolExecutor(max_workers=max(len(tasks), 1)) as ex:
        futures = [ex.submit(download_artifact, *t) for t in tasks]
        for f in as_completed(futures):
            f.result()

    # Distribute linux-ts-native staging files to their final destinations
    staging = root / "_linux_ts_staging"
    if staging.exists():
        so = staging / "libjava-tree-sitter.so"
        jar = staging / "java-tree-sitter-1.9.1.jar"
        if so.exists():
            dest = root / "libs" / "linux-x64"
            dest.mkdir(parents=True, exist_ok=True)
            shutil.copy2(so, dest / so.name)
            log(f"  linux-x64: ['{so.name}']")
        if jar.exists():
            shutil.copy2(jar, root / "mode" / jar.name)
            log(f"  mode: ['{jar.name}'] (patched with glibc .so)")
        shutil.rmtree(staging)

# ── Zip assembly ───────────────────────────────────────────────────────────────

def should_prune_dir(path):
    rel   = os.path.relpath(path, CPPMODE_DIR)
    parts = rel.split(os.sep)
    return len(parts) >= 1 and parts[0] in (".git", ".github")

def should_skip_file(path):
    rel   = os.path.relpath(path, CPPMODE_DIR)
    parts = rel.split(os.sep)
    return len(parts) >= 1 and parts[0] in (".git", ".github")

def build_zip(version):
    zip_name = f"CppMode-{version}.zip" if version else "CppMode.zip"
    zip_path = os.path.join(OUTPUT_DIR, zip_name)
    if os.path.exists(zip_path):
        os.remove(zip_path)

    included = skipped = 0
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(CPPMODE_DIR):
            dirs[:] = [d for d in dirs
                       if not should_prune_dir(os.path.join(root, d))]
            rel_dir = os.path.relpath(root, CPPMODE_DIR)
            kept_any = any(
                not should_skip_file(os.path.join(root, f)) for f in files)
            if not kept_any:
                zf.write(root, os.path.join("CppMode", rel_dir) + "/")
            for fname in files:
                full = os.path.join(root, fname)
                if should_skip_file(full):
                    skipped += 1
                    continue
                arcname = os.path.join(
                    "CppMode", os.path.relpath(full, CPPMODE_DIR))
                zf.write(full, arcname)
                included += 1

    size_mb = os.path.getsize(zip_path) / (1024 * 1024)
    log(f"\nCreated {zip_path} ({size_mb:.2f} MB)")
    log(f"  {included} files included, {skipped} skipped")

# ── Main ───────────────────────────────────────────────────────────────────────

WORKFLOWS = [
    "build-cache.yml",            # → cache/linux-x64, windows-x64, macos-*/
                                  #   + libs/windows-x64 runtime DLLs
    "build-linux-ts-native.yml",  # → libs/linux-x64/libjava-tree-sitter.so
]

def main():
    version = sys.argv[1] if len(sys.argv) > 1 else None
    if version:
        bump_version_files(version)

    log(f"\nTriggering {len(WORKFLOWS)} CI workflow(s) in parallel…")
    run_ids = {}
    with ThreadPoolExecutor(max_workers=len(WORKFLOWS)) as ex:
        futures = {ex.submit(trigger_and_wait, wf): wf for wf in WORKFLOWS}
        for f in as_completed(futures):
            wf, run_id = f.result()
            run_ids[wf] = run_id
            if run_id is None:
                log(f"  WARNING: {wf} did not complete successfully — its artifacts will be skipped")

    download_all_artifacts(run_ids)
    build_zip(version)

if __name__ == "__main__":
    main()
