#!/usr/bin/env python3
"""
Single, authoritative way to rebuild and deploy CppMode.jar.

Runs the same sequence that's been typed by hand throughout
development. Looks for the processing4 checkout via the PROCESSING4_DIR
environment variable first; if that isn't set, asks for the path
interactively instead of failing -- so this script works for anyone,
not just on a machine that already has PROCESSING4_DIR exported.

Setup (optional, skips the prompt every time):
    export PROCESSING4_DIR=/path/to/your/processing4
    (add that line to your ~/.bashrc to make it permanent)

Usage:
    rebuild-jar.py
"""
import subprocess
import sys
import os
import glob

SKETCHBOOK_JAR_DEST = os.path.expanduser("~/sketchbook/modes/CppMode/mode/CppMode.jar")
TMP_DIR = "/tmp/_cpp"

def run(cmd, cwd=None):
    print(f"$ {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=cwd)
    if result.returncode != 0:
        print(f"FAILED (exit code {result.returncode}): {' '.join(cmd)}")
        sys.exit(result.returncode)

def get_processing4_dir():
    env_dir = os.environ.get("PROCESSING4_DIR")
    if env_dir:
        env_dir = os.path.abspath(os.path.expanduser(env_dir))
        if os.path.isdir(env_dir):
            return env_dir
        print(f"WARNING: PROCESSING4_DIR is set to '{env_dir}', but that's not a real directory.")

    entered = input("Path to your processing4 checkout: ").strip()
    entered = os.path.abspath(os.path.expanduser(entered))
    if not os.path.isdir(entered):
        print(f"ERROR: '{entered}' is not a directory.")
        sys.exit(1)
    return entered

def main():
    processing4_dir = get_processing4_dir()
    cppbuild_java = os.path.join(processing4_dir, "java/src/processing/mode/cpp/CppBuild.java")
    libs_dir = os.path.join(processing4_dir, "java/build/libs")
    # Handle both "java.jar" (older builds) and versioned "java-X.Y.Z.jar" (newer)
    _candidates = (
        glob.glob(os.path.join(libs_dir, "java.jar")) +
        glob.glob(os.path.join(libs_dir, "java-*.jar"))
    )
    java_jar = _candidates[0] if _candidates else os.path.join(libs_dir, "java.jar")
    gradlew = os.path.join(processing4_dir, "gradlew")

    if not os.path.exists(cppbuild_java):
        print(f"ERROR: expected CppBuild.java not found at {cppbuild_java}")
        print("Is this really a processing4 checkout with CppMode's source linked in?")
        sys.exit(1)

    print(f"Using processing4 at: {processing4_dir}")
    os.utime(cppbuild_java, None)

    # Delete build artifacts to force recompilation without triggering
    # the Kotlin daemon (which fails on this system with --rerun-tasks)
    import shutil as _shutil
    for d in ["java/build/classes", "java/build/libs"]:
        p = os.path.join(processing4_dir, d)
        if os.path.exists(p): _shutil.rmtree(p)
    run([gradlew, ":java:compileJava", ":java:jar", "--no-daemon"], cwd=processing4_dir)

    _c2 = glob.glob(os.path.join(libs_dir, "java.jar")) + glob.glob(os.path.join(libs_dir, "java-*.jar"))
    if _c2: java_jar = _c2[0]
    if not os.path.exists(java_jar):
        print(f"ERROR: expected jar not found at {java_jar}")
        sys.exit(1)

    run(["rm", "-rf", TMP_DIR])
    run(["mkdir", TMP_DIR])
    run(["jar", "xf", java_jar], cwd=TMP_DIR)
    os.makedirs(os.path.dirname(SKETCHBOOK_JAR_DEST), exist_ok=True)
    run(["jar", "cf", SKETCHBOOK_JAR_DEST, "processing/mode/cpp/"], cwd=TMP_DIR)

    size = os.path.getsize(SKETCHBOOK_JAR_DEST)
    print()
    print(f"Done. Deployed CppMode.jar ({size} bytes) to {SKETCHBOOK_JAR_DEST}")

if __name__ == "__main__":
    main()

# Sync to Processing bundled mode dirs so IDE picks up changes immediately
import shutil, pathlib
jar = pathlib.Path("/home/pep/sketchbook/modes/CppMode/mode/CppMode.jar")
for dest in [
    "/home/pep/Projects/processing4/app/build/resources-bundled/common/modes/CppMode/mode/CppMode.jar",
    "/home/pep/Projects/processing4/app/build/compose/tmp/prepareAppResources/modes/CppMode/mode/CppMode.jar",
]:
    pathlib.Path(dest).parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, dest)
    print(f"Synced to {dest}")
