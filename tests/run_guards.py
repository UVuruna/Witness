"""Project guard runner (template: rules/templates/run_guards.py).

Wired via `.claude/settings.json`:
    PostToolUse -> python tests/run_guards.py --fast
    Stop        -> python tests/run_guards.py

--fast runs only the cheap guards. The full pass runs ONLY when
`changed_files.touched_anything()` says this session changed something —
"cannot tell" always means RUN, never skip. The full pass also runs the
clone guard against this project's ratchet and the rules-size guard.

Exit 2 on any guard failure — that is what makes the Stop hook BLOCK.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = TESTS_DIR.parent
REPO_ROOT = PROJECT_ROOT.parents[1]  # Applications/Safety -> monorepo root

FAST_GUARDS = [
    "tests/test_structure_law.py",
    "tests/test_config_sections.py",
]
FULL_GUARDS = list(FAST_GUARDS) + [
    "tests/test_docs_coverage.py",
    "tests/test_doc_links.py",
]


def _load(rel_path: str):
    """Load a monorepo-root module by path; None if it cannot be reached
    (an unreachable helper never silently disables a law — callers must
    treat None as "assume the worst / run everything")."""
    path = REPO_ROOT / rel_path
    try:
        spec = importlib.util.spec_from_file_location(path.stem, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    except (OSError, AttributeError, ImportError, SyntaxError):
        return None


def main(argv: list[str]) -> int:
    import pytest

    fast = "--fast" in argv

    if fast:
        targets = [str(PROJECT_ROOT / g) for g in FAST_GUARDS]
        code = pytest.main(["-q", "--no-header", *targets]) if targets else 0
        return _finish(code, "fast")

    changed = _load("rules/hooks/changed_files.py")
    if changed is not None and not changed.touched_anything(PROJECT_ROOT):
        print("guards: session changed no file — full pass skipped")
        return 0

    targets = [str(PROJECT_ROOT / g) for g in FULL_GUARDS]
    code = pytest.main(["-q", "--no-header", *targets]) if targets else 0
    if code != 0:
        return _finish(code, "full")

    clone_guard = _load("rules/tools/clone_guard.py")
    if clone_guard is not None:
        ratchet = PROJECT_ROOT / "tests" / "clone_ratchet.json"
        rc = clone_guard.run([str(PROJECT_ROOT), "--ratchet", str(ratchet)])
        if rc != 0:
            print("\nGUARD FAILURE (full pass) — clone_guard found an "
                  "un-ratcheted duplicate. Fix it or extend the ratchet.",
                  file=sys.stderr)
            return 2

    size_guard = _load("rules/tools/rules_size_guard.py")
    if size_guard is not None:
        rows = size_guard.check(project=PROJECT_ROOT)
        if any(not ok for _, _, _, ok, _ in rows):
            print("\nGUARD FAILURE (full pass) — a rulebook is over its "
                  "byte limit (rules_size_guard).", file=sys.stderr)
            return 2

    return _finish(0, "full")


def _finish(code: int, label: str) -> int:
    if code != 0:
        print(f"\nGUARD FAILURE ({label} pass) — fix the violation above "
              "before continuing.", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
