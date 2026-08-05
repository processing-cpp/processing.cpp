#!/usr/bin/env python3
"""
Build precompiled Processing.o, Processing_defaults.o, and Processing.h.gch
for the current platform into cache/<platform>/.

Run this on each target platform before creating a release.
The CI workflow (build-cache.yml) runs this on Linux, Windows, and macOS runners.

Flags used are conservative (no -march=native) so the .o files work on any
x86-64 machine, not just the build machine.
"""
import os, sys, subprocess, platform, shutil
from pathlib import Path

root    = Path(__file__).parent.parent
src     = root / "src"
procH   = src / "Processing.h"
procCpp = src / "Processing.cpp"
defCpp  = src / "Processing_defaults.cpp"

def platform_subdir():
    s = platform.system().lower()
    m = platform.machine().lower()
    if s == "windows":   return "windows-x64"
    if s == "darwin":
        return "macos-arm64" if ("arm" in m or "aarch64" in m) else "macos-x64"
    return "linux-x64"

def find_gpp():
    # Windows: check portable gcc first, then MSYS2
    if platform.system() == "Windows":
        appdata = os.environ.get("APPDATA", "")
        portable = Path(appdata) / "CppMode/gcc/mingw64/bin/g++.exe"
        if portable.exists(): return str(portable)
        for p in [r"C:\msys64\mingw64\bin\g++.exe",
                  r"C:\msys2\mingw64\bin\g++.exe"]:
            if Path(p).exists(): return p
    return "g++"

def run(cmd, desc):
    print(f"  {desc}...")
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(f"  FAILED: {r.stderr[:500]}")
        sys.exit(1)
    print(f"  OK")

subdir   = platform_subdir()
cacheDir = root / "cache" / subdir
cacheDir.mkdir(parents=True, exist_ok=True)

gpp = find_gpp()
print(f"Platform: {subdir}")
print(f"Compiler: {gpp}")
print(f"Cache:    {cacheDir}")

isMac = platform.system() == "Darwin"
isWin = platform.system() == "Windows"

# Conservative flags -- no -march=native, target baseline x86-64
baseFlags = [gpp, "-std=c++2c", "-O2", "-x86-64" if not isMac else ""]
baseFlags = [f for f in baseFlags if f]  # remove empty

includeFlags = ["-I", str(src)]
if isMac:
    for prefix in ["/opt/homebrew", "/usr/local"]:
        if Path(prefix + "/include").exists():
            includeFlags += ["-I", prefix + "/include"]
            break

defines = ["-DPROCESSING_HAS_STB_IMAGE", "-DPROCESSING_HAS_STB_TRUETYPE"]

# 1. Build Processing.h.gch
pchOut = cacheDir / "Processing.h.gch"
print(f"\nBuilding PCH...")
run([gpp, "-std=c++2c", "-O2",
     "-x", "c++-header",
     *includeFlags, *defines,
     str(procH), "-o", str(pchOut)],
    f"Processing.h → {pchOut.name}")

# 2. Build Processing.o
procO = cacheDir / "Processing.o"
print(f"\nBuilding Processing.o...")
run([gpp, "-std=c++2c", "-O2",
     *includeFlags, *defines,
     "-c", str(procCpp), "-o", str(procO)],
    f"Processing.cpp → {procO.name}")

# 3. Build Processing_defaults.o
defO = cacheDir / "Processing_defaults.o"
if defCpp.exists():
    print(f"\nBuilding Processing_defaults.o...")
    run([gpp, "-std=c++2c", "-O2",
         *includeFlags, *defines,
         "-c", str(defCpp), "-o", str(defO)],
        f"Processing_defaults.cpp → {defO.name}")

print(f"\nDone. Cache files in {cacheDir}:")
for f in sorted(cacheDir.iterdir()):
    print(f"  {f.name}  ({f.stat().st_size // 1024} KB)")
