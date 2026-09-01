"""Guard: THE STRUCTURE LAW (root CLAUDE.md -> The Laws, Priority S). No
source file may exceed ~1,000 lines unless it is in the RATCHET allowlist
below. Each allowlist entry documents WHY the file stays whole and which
session owes the split. The allowlist may only SHRINK — adding an entry
requires the owner's explicit approval in that same session.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _guards_common import PROJECT_ROOT, iter_source_files  # noqa: E402

THRESHOLD = 1000

# RATCHET allowlist — empty since founding (2026-09-01). May only shrink.
RATCHET: dict[str, str] = {}


def test_no_file_exceeds_structure_law_threshold():
    violations = []
    for path in iter_source_files():
        with path.open(encoding="utf-8", errors="replace") as f:
            line_count = sum(1 for _ in f)
        if line_count <= THRESHOLD:
            continue
        rel = path.relative_to(PROJECT_ROOT).as_posix()
        if rel in RATCHET:
            continue
        violations.append(f"{rel}: {line_count} lines (> {THRESHOLD}, no RATCHET entry)")
    assert not violations, "THE STRUCTURE LAW violated:\n" + "\n".join(violations)


def test_ratchet_entries_reference_existing_files():
    # A ratchet entry for a file that no longer exists (renamed/split/deleted)
    # is dead weight to clean up, not to carry forward silently.
    missing = [rel for rel in RATCHET if not (PROJECT_ROOT / rel).exists()]
    assert not missing, f"RATCHET entries for files that no longer exist: {missing}"


def test_no_log_file_lands_in_the_project_root():
    # Owner ruling 2026-08-16 (monorepo-wide): a log may never end up in the
    # project root, and .gitignore is deliberately NOT the answer — an
    # ignored log is hidden, not prevented. The app writes to its own app
    # data dir; probes and harnesses capture into their own temp dirs.
    strays = sorted(p.name for p in PROJECT_ROOT.glob("*.log"))
    assert not strays, (
        "Log file(s) in the project root: " + ", ".join(strays)
        + " — delete them and redirect the writer."
    )


if __name__ == "__main__":
    test_no_file_exceeds_structure_law_threshold()
    test_ratchet_entries_reference_existing_files()
    test_no_log_file_lands_in_the_project_root()
    print("PASS — test_structure_law")
