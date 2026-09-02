"""Guard: MD-First 2.0 tier coverage (rules/DOCS.md -> Tiers). Every source
file must have the docs its tier requires:

  Trivial     -> no own doc (a one-line mention in the folder's ___folder.md)
  Standard    -> __about/{name}.md
  Algorithmic -> __about/{name}.md AND __flow/{name}.md
  tests/      -> no own doc (___tests.md covers the folder)

The tier lists below are the single source of truth for tier assignment —
changing a file's tier means updating this test in the same commit. Any
project source file not listed in exactly one tier is a build failure.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _guards_common import PROJECT_ROOT, iter_source_files  # noqa: E402

# M0 CALL-AUDIO PROBE (2026-09-01): a throwaway feasibility harness, not the
# product. It answers ONE question — what does the microphone actually capture
# during a phone call — then gets replaced by real subsystems at M1. Because it
# is a single coherent throwaway module, its files are Trivial (documented as a
# whole in app/___app.md), NOT Standard subsystems owed one __about doc each.
# When M1 product code lands, files graduate to their real tier with __about
# docs in the same commit.
TRIVIAL: set[str] = {
    "app/src/main/java/com/uvuruna/callprobe/MainActivity.kt",
    "app/src/main/java/com/uvuruna/callprobe/audio/WavRecorder.kt",
    "app/src/main/java/com/uvuruna/callprobe/audio/Playback.kt",
    "app/probe-tools/CallCap.java",
    "app/src/main/java/com/uvuruna/callprobe/audio/RecordingStore.kt",
    "app/src/main/java/com/uvuruna/callprobe/service/RecorderService.kt",
    "app/src/main/java/com/uvuruna/callprobe/call/CallMonitor.kt",
    "app/src/main/java/com/uvuruna/callprobe/listen/ListenService.kt",
    "app/src/main/java/com/uvuruna/callprobe/listen/ListenLog.kt",
    "app/src/main/java/com/uvuruna/callprobe/ui/ProbeViewModel.kt",
    "app/src/main/java/com/uvuruna/callprobe/ui/ProbeScreen.kt",
}
STANDARD: set[str] = set()
ALGORITHMIC: set[str] = set()

ALL_CLASSIFIED = TRIVIAL | STANDARD | ALGORITHMIC


def _is_tests_tier(rel_posix: str) -> bool:
    return rel_posix == "tests" or rel_posix.startswith("tests/")


def _about_flow_paths(rel: Path) -> tuple[Path, Path]:
    """Where a source file's __about/__flow docs live: beside the file's own
    folder. When the Android Gradle tree arrives (app/src/main/java/...),
    its Kotlin sources will keep their docs at the app/ top level next to
    app/___app.md — docs mirror the doc-folder tree, not the Java package
    tree (the VibeCoder precedent)."""
    basename = rel.stem
    if rel.parts[0] == "app":
        folder = PROJECT_ROOT / "app"
    else:
        folder = PROJECT_ROOT / rel.parent
    return folder / "__about" / f"{basename}.md", folder / "__flow" / f"{basename}.md"


def test_every_source_file_is_classified():
    unclassified = []
    for path in iter_source_files():
        rel = path.relative_to(PROJECT_ROOT).as_posix()
        if _is_tests_tier(rel) or rel in ALL_CLASSIFIED:
            continue
        unclassified.append(rel)
    assert not unclassified, (
        "Source files with no tier assignment in test_docs_coverage.py "
        "(classify them: Trivial/Standard/Algorithmic — DOCS.md -> Tiers):\n"
        + "\n".join(unclassified)
    )


def test_standard_and_algorithmic_files_have_required_docs():
    missing = []
    for path in iter_source_files():
        rel_str = path.relative_to(PROJECT_ROOT).as_posix()
        if rel_str not in STANDARD and rel_str not in ALGORITHMIC:
            continue
        rel = path.relative_to(PROJECT_ROOT)
        about_path, flow_path = _about_flow_paths(rel)
        if not about_path.exists():
            missing.append(f"{rel_str}: missing {about_path.relative_to(PROJECT_ROOT).as_posix()}")
        if rel_str in ALGORITHMIC and not flow_path.exists():
            missing.append(f"{rel_str}: missing {flow_path.relative_to(PROJECT_ROOT).as_posix()} (Algorithmic tier)")
    assert not missing, "Docs coverage gaps:\n" + "\n".join(missing)


def test_trivial_and_tests_tier_files_have_no_stray_doc():
    stray = []
    for path in iter_source_files():
        rel_str = path.relative_to(PROJECT_ROOT).as_posix()
        if rel_str not in TRIVIAL and not _is_tests_tier(rel_str):
            continue
        rel = path.relative_to(PROJECT_ROOT)
        about_path, flow_path = _about_flow_paths(rel)
        for doc in (about_path, flow_path):
            if doc.exists():
                stray.append(str(doc.relative_to(PROJECT_ROOT).as_posix()))
    assert not stray, (
        "Trivial/tests-tier files with a stray __about/__flow doc "
        "(promote the tier or delete the doc):\n" + "\n".join(stray)
    )


def test_every_code_folder_has_a_folder_doc():
    # Every folder holding at least one CLASSIFIED source file needs its own
    # ___{folder}.md entry point; app/'s deep Java package tree maps to
    # app/___app.md (see _about_flow_paths). tests/ has ___tests.md, checked
    # here too via its own folder.
    folders_with_code = set()
    for path in iter_source_files():
        rel = path.relative_to(PROJECT_ROOT)
        rel_str = rel.as_posix()
        if rel_str not in ALL_CLASSIFIED and not _is_tests_tier(rel_str):
            continue
        folders_with_code.add(
            PROJECT_ROOT / "app" if rel.parts[0] == "app"
            else PROJECT_ROOT / "tests" if _is_tests_tier(rel_str)
            else path.parent
        )
    missing = []
    for folder in folders_with_code:
        expected = folder / f"___{folder.name}.md"
        if not expected.exists():
            missing.append(str(expected.relative_to(PROJECT_ROOT).as_posix()))
    assert not missing, "Code folders missing their ___folder.md entry point:\n" + "\n".join(missing)


if __name__ == "__main__":
    test_every_source_file_is_classified()
    test_standard_and_algorithmic_files_have_required_docs()
    test_trivial_and_tests_tier_files_have_no_stray_doc()
    test_every_code_folder_has_a_folder_doc()
    print("PASS — test_docs_coverage")
