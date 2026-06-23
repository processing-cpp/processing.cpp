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
    java_jar = os.path.join(processing4_dir, "java/build/libs/java.jar")
    gradlew = os.path.join(processing4_dir, "gradlew")

    if not os.path.exists(cppbuild_java):
        print(f"ERROR: expected CppBuild.java not found at {cppbuild_java}")
        print("Is this really a processing4 checkout with CppMode's source linked in?")
        sys.exit(1)

    print(f"Using processing4 at: {processing4_dir}")
    os.utime(cppbuild_java, None)

    run([gradlew, ":java:compileJava", "--rerun-tasks"], cwd=processing4_dir)
    run([gradlew, ":java:jar"], cwd=processing4_dir)

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
