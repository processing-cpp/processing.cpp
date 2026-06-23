#!/usr/bin/env python3
"""
extract_user_api.py -- extracts every function, method, class, and
keyword reachable from a CppMode sketch into a structured YAML file,
for you to review and prune.

Requires `ctags` (universal-ctags) to be installed:
    sudo pacman -S universal-ctags          # Arch
    sudo apt install universal-ctags        # Debian/Ubuntu

This does NOT regex-guess at C++ syntax (templates, overloads, and
multi-line signatures make that unreliable) -- it uses ctags, a real
C++ parser, to get scope-aware symbol data: which namespace/class a
function or method belongs to, its signature, and its source line.

WHAT COUNTS AS "USER-FACING" (and the reasoning, so you can audit it):

  INCLUDED:
  - Free functions declared directly in the Processing namespace
    (rect, fill, loadImage, etc.) -- this is the literal Processing API
    surface a sketch calls without an object.
  - Methods on classes ALSO declared directly in the Processing
    namespace (IntList::append, PVector::add, String::substring, etc.)
    -- since the class itself is reachable from sketch code, its public
    methods are too.
  - Classes/structs declared directly in the Processing namespace.
  - setup/draw/mousePressed/keyPressed/etc. -- the event-callback hooks
    a sketch DEFINES (these appear as free functions too, since that's
    how a sketch declares them, but are tagged separately as "hooks"
    in the output so you can tell them apart from things the user CALLS).
  - Keywords from keywords.txt (parsed separately, see below).

  EXCLUDED (with the specific reason each was caught):
  - Anything in Processing::_api -- this is the internal implementation
    namespace; the public free functions of the same names (rect,
    fill, background, ...) forward to these, so they're pure
    duplicates from a different layer, never called directly by a
    sketch as _api::something().
  - PApplet's METHODS -- PApplet is a singleton (g_papplet) that the
    namespace-scope free functions forward to internally (e.g. rect()
    secretly calls g_papplet->rect()). A sketch never writes
    PApplet::something() or g_papplet-> directly, so its methods are
    implementation detail, not a second user-facing surface. PApplet's
    FIELDS (width, mouseX, keyCode, etc.) ARE real user-facing globals
    though, and are listed separately under "system_variables" --
    these are the ACTUAL public part PApplet's own comment refers to.
  - IsJavaValueType -- an internal type-trait helper for ArrayList<T>'s
    value/reference storage selection, never referenced by sketch code.
  - Anything whose name matches a GL-related pattern (gl*, GL_*, *GLFW*,
    *Shader* is INCLUDED though, since PShader is a real public class --
    only literal OpenGL/GLFW plumbing is excluded). See GL_EXCLUDE_PATTERNS.
  - Anything starting with `_` or `cb_` -- your own internal-naming
    convention for private state and GLFW callback trampolines.
  - Operators (operator+, operator<<, etc.) -- excluded by default since
    they're rarely called as a NAMED function by sketch code; flip
    INCLUDE_OPERATORS to True below if you want them listed too.

  FLAGGED FOR YOUR REVIEW (included, but marked uncertain):
  - color (struct) and PColor (class) -- both look plausibly user-facing
    color-handling types, but it's not certain from static analysis
    alone whether PColor is ever directly user-visible vs. purely an
    internal parameter-conversion helper for fill()/stroke(). Marked
    with a "review" flag in the output -- check these yourself and
    delete the entry (or tell me to) if either turns out to be internal.

USAGE:
    python3 extract_user_api.py
    python3 extract_user_api.py --output my_api.yml
    python3 extract_user_api.py --namespace Processing   # if your engine
                                                          # uses a different
                                                          # top-level namespace
"""
import argparse
import json
import os
import re
import subprocess
import sys

KEYWORDS_FILENAME = "keywords.txt"
ENGINE_FILES = ["src/Processing.h", "src/Processing.cpp"]

# Internal namespaces/classes whose MEMBERS are excluded wholesale, with
# the reason documented at the point of use in main().
EXCLUDED_SCOPES = {
    "Processing::_api",      # internal implementation layer, public free
                              # functions of the same names forward to these
    "Processing::PApplet",   # singleton backing object; MOST of its methods
                              # are never called directly by sketch code --
                              # BUT see KNOWN_HOOK_NAMES below: setup/draw/
                              # mousePressed/etc. are virtual methods on
                              # PApplet that a sketch overrides (confirmed
                              # via "virtual void setup() {}" in source) --
                              # those specific names are NOT excluded even
                              # though they live in this scope.
}

EXCLUDED_NAMES = {
    "IsJavaValueType",  # internal type-trait helper, not user-facing
}

# Names that get extra scrutiny/flagging rather than outright exclusion,
# since static analysis alone can't be fully certain about them.
REVIEW_NAMES = {
    "color": "Real struct backing the 'color' datatype keyword -- almost "
             "certainly genuinely user-facing (sketches write color c = "
             "color(255,0,0)). Flagged just for your final confirmation.",
    "PColor": "Looks plausibly user-facing (alternate float-based color "
              "type), but might be purely an internal parameter-conversion "
              "helper used by fill()/stroke(). Verify whether sketch code "
              "ever references PColor directly before keeping this.",
}

GL_EXCLUDE_PATTERNS = [
    re.compile(r"^gl[A-Z_]"),       # glXxx, GL_XXX-style OpenGL calls
    re.compile(r"GLFW", re.I),
    re.compile(r"^cb_"),            # GLFW callback trampolines (prefix style)
    re.compile(r"_cb$"),            # GLFW callback trampolines (suffix style,
                                     # e.g. key_cb, mouse_btn_cb, scroll_cb,
                                     # cursor_pos_cb, winpos_cb, winsize_cb,
                                     # fbsize_cb, focus_cb, char_cb -- confirmed
                                     # present and unfiltered in a real run)
]

INCLUDE_OPERATORS = False

# Event-callback hooks a sketch DEFINES (overrides), not calls. Confirmed
# via source inspection that these are declared as virtual methods on
# PApplet ("virtual void setup() {}" etc.) that a sketch's translation
# unit overrides -- NOT free functions in the simple sense, but the actual
# mechanism a sketch uses to hook into the engine. Carved out from the
# PApplet-methods exclusion specifically for this reason.
KNOWN_HOOK_NAMES = {
    "setup", "draw", "settings",
    "mousePressed", "mouseReleased", "mouseClicked", "mouseMoved",
    "mouseDragged", "mouseWheel",
    "keyPressed", "keyReleased", "keyTyped",
    "windowMoved", "windowResized",
}


def find_repo_root():
    candidates = [os.getcwd(), os.path.dirname(os.path.abspath(__file__))]
    for start in candidates:
        d = start
        for _ in range(6):
            if os.path.exists(os.path.join(d, "src", "Processing.h")):
                return d
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent
    return os.getcwd()


def check_ctags():
    try:
        result = subprocess.run(["ctags", "--version"], capture_output=True, text=True)
        if "Universal Ctags" not in result.stdout:
            print("WARNING: found a 'ctags' binary but it doesn't look like Universal Ctags.")
            print("This script needs Universal Ctags specifically (not legacy exuberant-ctags).")
            print(f"Version output was: {result.stdout[:200]}")
    except FileNotFoundError:
        print("ERROR: 'ctags' not found.")
        print("Install Universal Ctags first:")
        print("  Arch:           sudo pacman -S universal-ctags")
        print("  Debian/Ubuntu:  sudo apt install universal-ctags")
        sys.exit(1)


def run_ctags(files, tmp_path):
    cmd = [
        "ctags", "--languages=C++", "--kinds-C++=fmcsv",
        "--fields=+nKSstm", "-f", tmp_path, "--output-format=json",
    ] + files
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print("ERROR running ctags:")
        print(result.stderr)
        sys.exit(1)


def load_tags(tmp_path):
    tags = []
    with open(tmp_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if obj.get("_type") == "tag":
                tags.append(obj)
    return tags


def is_gl_related(name):
    return any(p.search(name) for p in GL_EXCLUDE_PATTERNS)


def is_internal_naming(name):
    """Your own internal-naming convention: a leading underscore marks
    private/internal state or helpers (_colorMaxA, _makeColor,
    _doEnableDebugConsole, etc.), and cb_ marks GLFW callback trampolines
    (cb_mouse_btn, cb_key, etc.). Neither is meant to be called directly
    by sketch code."""
    return name.startswith("_") or name.startswith("cb_")


def is_operator(name):
    return name.startswith("operator")


def parse_keywords(path):
    """Returns list of (identifier, tag) from keywords.txt, in file order,
    skipping comments/blanks. Duplicates (same identifier, different tag)
    are preserved -- see prior conversation about dual-tag entries like
    color/size having two legitimate rows."""
    entries = []
    if not os.path.exists(path):
        return entries
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            parts = re.split(r"\s+", stripped)
            if len(parts) >= 2:
                entries.append({"identifier": parts[0], "tag": parts[1]})
    return entries


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="user_api.yml", help="output YAML path")
    parser.add_argument("--namespace", default="Processing", help="top-level engine namespace")
    args = parser.parse_args()

    check_ctags()
    repo_root = find_repo_root()

    engine_files = [os.path.join(repo_root, p) for p in ENGINE_FILES if os.path.exists(os.path.join(repo_root, p))]
    if not engine_files:
        print(f"ERROR: none of {ENGINE_FILES} found under {repo_root}")
        sys.exit(1)

    keywords_path = os.path.join(repo_root, KEYWORDS_FILENAME)

    # Resolve --output against the repo root by default (matching
    # sync_keywords.py's behavior) so this script writes to the same
    # place whether invoked as `python3 extract_user_api.py` from inside
    # scripts/ or as `python3 scripts/extract_user_api.py` from the repo
    # root. An absolute path, or one with its own directory component
    # (e.g. "--output /tmp/out.yml" or "--output build/out.yml"), is
    # respected as-is rather than re-rooted.
    if not os.path.isabs(args.output) and os.path.dirname(args.output) == "":
        args.output = os.path.join(repo_root, args.output)

    tmp_tags_path = "/tmp/_extract_user_api_tags.json"
    run_ctags(engine_files, tmp_tags_path)
    tags = load_tags(tmp_tags_path)
    os.remove(tmp_tags_path)

    ns = args.namespace

    # ---- free functions: declared directly in the namespace ----
    free_funcs_by_name = {}
    for t in tags:
        if t.get("kind") != "function":
            continue
        if t.get("scope") != ns or t.get("scopeKind") != "namespace":
            continue
        name = t["name"]
        if name in EXCLUDED_NAMES or is_gl_related(name) or is_internal_naming(name):
            continue
        if is_operator(name) and not INCLUDE_OPERATORS:
            continue
        entry = free_funcs_by_name.setdefault(name, {
            "name": name, "kind": "function", "signatures": [], "lines": []
        })
        entry["signatures"].append(t.get("signature", ""))
        entry["lines"].append(t.get("line"))

    # ---- namespace-scope variables (e.g. static constexpr float PI = ...) ----
    # ctags' "function" kind only covers actual function definitions --
    # constants like PI/TWO_PI/HALF_PI/QUARTER_PI/TAU are variable
    # definitions (kind "v"), and were previously missed entirely by this
    # script's extraction (they only appeared via the separate keywords.txt
    # passthrough, unverified against the engine source). Collected here
    # as their own category so they're structurally verified like everything
    # else.
    namespace_vars_by_name = {}
    for t in tags:
        if t.get("kind") != "variable":
            continue
        if t.get("scope") != ns or t.get("scopeKind") != "namespace":
            continue
        name = t["name"]
        if name in EXCLUDED_NAMES or is_gl_related(name) or is_internal_naming(name):
            continue
        entry = namespace_vars_by_name.setdefault(name, {
            "name": name, "line": t.get("line"), "type": t.get("typeref", "")
        })

    # ---- classes/structs declared directly in the namespace ----
    classes_by_name = {}
    for t in tags:
        if t.get("kind") not in ("class", "struct"):
            continue
        if t.get("scope") != ns or t.get("scopeKind") != "namespace":
            continue
        name = t["name"]
        if name in EXCLUDED_NAMES:
            continue
        full_scope = f"{ns}::{name}"
        if full_scope in EXCLUDED_SCOPES:
            continue
        entry = classes_by_name.setdefault(name, {
            "name": name, "kind": t["kind"], "line": t.get("line"),
            "methods": {}, "fields": [],
        })
        if name in REVIEW_NAMES:
            entry["review"] = REVIEW_NAMES[name]

    # ---- methods/members belonging to those classes ----
    for t in tags:
        scope = t.get("scope", "")
        if not scope.startswith(f"{ns}::"):
            continue
        class_name = scope[len(ns) + 2:]
        # only direct members of a namespace-scope class we're tracking
        # (skip deeper-nested scopes like Processing::Foo::Bar)
        if "::" in class_name:
            continue
        if class_name not in classes_by_name:
            continue  # belongs to an excluded/untracked scope (e.g. PApplet, _api)
        name = t["name"]
        if name in EXCLUDED_NAMES or is_gl_related(name) or is_internal_naming(name):
            continue
        if t.get("kind") == "function":
            if is_operator(name) and not INCLUDE_OPERATORS:
                continue
            if name == class_name:
                continue  # constructor, not a user-callable "method" per se
            entry = classes_by_name[class_name]["methods"].setdefault(name, {
                "name": name, "kind": "method", "signatures": [], "lines": []
            })
            entry["signatures"].append(t.get("signature", ""))
            entry["lines"].append(t.get("line"))
        elif t.get("kind") == "member":
            classes_by_name[class_name]["fields"].append({
                "name": name, "line": t.get("line"), "type": t.get("typeref", "")
            })

    # ---- PApplet's FIELDS specifically (real system variables) ----
    # PApplet's source has an explicit "//── Public state (directly
    # accessible in sketch code) ──" comment, but even within that
    # section several fields are clearly internal render state
    # (fillR/strokeR/colorModeVal/pixels/g_sketchName/etc.), not things a
    # sketch reads directly (a sketch calls fill(), it doesn't read
    # g_papplet.fillR). That distinction needs intent that isn't visible
    # from static analysis alone -- this list excludes only underscore-
    # prefixed and GL-related names (same convention as everywhere else
    # in this script) and is otherwise left complete and flagged for your
    # manual review, per your stated preference to see everything and
    # decide what to cut yourself.
    system_variables = []
    for t in tags:
        if t.get("kind") == "member" and t.get("scope") == f"{ns}::PApplet":
            name = t["name"]
            if name.startswith("_") or is_gl_related(name):
                continue
            system_variables.append({"name": name, "line": t.get("line"), "type": t.get("typeref", "")})

    # ---- event-callback hooks ----
    # These are things a sketch DEFINES (overrides), not calls -- setup,
    # draw, and the mouse/key event handlers. They may appear as free
    # functions (older/simple architecture) or as virtual PApplet methods
    # a sketch overrides (confirmed in this engine's actual source) --
    # collected from BOTH possible locations so the output is correct
    # either way, then excluded from the plain "functions you call" list.
    hooks_found = set(free_funcs_by_name.keys()) & KNOWN_HOOK_NAMES
    for t in tags:
        if t.get("kind") == "function" and t.get("scope") == f"{ns}::PApplet" and t["name"] in KNOWN_HOOK_NAMES:
            hooks_found.add(t["name"])
    hooks = sorted(hooks_found)
    callable_functions = {k: v for k, v in free_funcs_by_name.items() if k not in KNOWN_HOOK_NAMES}

    # ---- keywords.txt ----
    keywords = parse_keywords(keywords_path)

    # ---- assemble output ----
    def clean_func_list(d):
        out = []
        for name, entry in sorted(d.items()):
            sigs = sorted(set(s for s in entry["signatures"] if s))
            out.append({
                "name": name,
                "overload_count": len(entry["lines"]),
                "signatures": sigs if sigs else None,
                "lines": sorted(set(entry["lines"])),
            })
        return out

    classes_out = []
    for name, c in sorted(classes_by_name.items()):
        item = {
            "name": name,
            "kind": c["kind"],
            "line": c["line"],
            "methods": clean_func_list(c["methods"]),
            "fields": sorted(c["fields"], key=lambda f: f["name"]) if c["fields"] else [],
        }
        if "review" in c:
            item["review"] = c["review"]
        classes_out.append(item)

    output = {
        "_generated_by": "extract_user_api.py",
        "_namespace": ns,
        "_notes": (
            "Auto-extracted from real engine source via ctags (a real C++ "
            "parser, not regex guessing). Review the 'review' flags and "
            "excluded-scope comments at the top of this script before "
            "trusting any single entry -- some classifications (e.g. "
            "color vs PColor) needed a judgment call and are marked."
        ),
        "callback_hooks": sorted(hooks),
        "functions": clean_func_list(callable_functions),
        "classes": classes_out,
        "system_variables": sorted(system_variables, key=lambda v: v["name"]),
        "_system_variables_review_note": (
            "This list is NOT filtered beyond excluding underscore-prefixed "
            "and GL-related names. It mixes genuine sketch-facing globals "
            "(width, height, mouseX, keyCode, frameCount, ...) with internal "
            "render state (fillR, strokeR, colorModeVal, pixels, "
            "g_sketchName, ...) that a sketch never reads directly -- the "
            "engine source's own 'Public state' comment covers both groups, "
            "so static analysis alone can't separate them reliably. Review "
            "this list and tell me which ones to cut."
        ),
        "namespace_constants": sorted(namespace_vars_by_name.values(), key=lambda v: v["name"]),
        "keywords": keywords,
    }

    try:
        import yaml
        with open(args.output, "w", encoding="utf-8") as f:
            yaml.dump(output, f, sort_keys=False, default_flow_style=False, allow_unicode=True)
    except ImportError:
        print("NOTE: PyYAML not installed -- writing JSON instead (same structure).")
        print("Install with: pip install pyyaml --break-system-packages")
        json_path = args.output.rsplit(".", 1)[0] + ".json"
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(output, f, indent=2)
        args.output = json_path

    print(f"Wrote: {args.output}")
    print(f"  {len(hooks)} callback hooks")
    print(f"  {len(callable_functions)} free functions ({sum(c['overload_count'] for c in clean_func_list(callable_functions))} total overloads)")
    print(f"  {len(classes_out)} classes/structs")
    print(f"  {len(system_variables)} system variables (PApplet public fields)")
    print(f"  {len(namespace_vars_by_name)} namespace-scope constants (PI, TWO_PI, etc.)")
    print(f"  {len(keywords)} keyword entries from {KEYWORDS_FILENAME}")
    review_count = sum(1 for c in classes_out if "review" in c)
    if review_count:
        print(f"  ({review_count} classes flagged for your manual review -- search for 'review:' in the output)")


if __name__ == "__main__":
    main()
