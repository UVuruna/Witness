"""Shared helpers for the project's guard tests (test_structure_law,
test_config_sections, test_docs_coverage, test_doc_links). Not a test module
itself — no `test_` prefix, pytest will not collect it.
"""

import os
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent

# Directories never scanned by any guard: build output, VCS internals, the
# owner's gitignored UV/ inbox, and the .claude/ harness config. PRUNED
# during the walk (never descended into) so the guard runner stays fast.
EXCLUDE_DIR_NAMES = {
    ".git", "__pycache__", ".pytest_cache", "UV", ".claude",
    ".gradle", ".kotlin", ".idea", "build", "captures", "logs",
}

# Kotlin app + Python guard tooling; every source language counts toward
# THE STRUCTURE LAW, never Python only.
SOURCE_EXTENSIONS = {".py", ".kt", ".kts", ".js", ".ts", ".html", ".css"}

# Gradle build scripts are declarative BUILD config, not product source: they
# are governed by the build itself, carry no tier and own no __about doc. They
# would otherwise be swept in as .kts source. (The CONFIG SECTION law owns
# config surfaces separately.)
EXCLUDE_FILE_NAMES = {
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
}


def _walk_files(suffixes: set[str]):
    for dirpath, dirnames, filenames in os.walk(PROJECT_ROOT):
        dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIR_NAMES]
        for name in filenames:
            if name in EXCLUDE_FILE_NAMES:
                continue
            if Path(name).suffix in suffixes:
                yield Path(dirpath) / name


def iter_source_files(extensions=SOURCE_EXTENSIONS):
    """Every project source file with one of `extensions`, excluded dirs
    pruned (not just filtered)."""
    yield from _walk_files(extensions)


def iter_doc_files():
    """Every project .md file, same pruning."""
    yield from _walk_files({".md"})
