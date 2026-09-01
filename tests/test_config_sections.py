"""Guard: THE CONFIG SECTION LAW (root CLAUDE.md -> The Laws). Every file
listed in CONFIG_FILES must have every top-level definition sitting under a
`# ══...══` section banner, must never post-definition-patch an earlier
module-level table (`TABLE[...] = ...` outside the table's own definition),
and must never define a dict literal with duplicate keys.

Python-only (AST-based). Kotlin config surfaces get their own check the day
one exists — add it here in the same commit, per the law.
"""

import ast
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _guards_common import PROJECT_ROOT  # noqa: E402

# Empty since founding (2026-09-01): no product code exists yet. Add a file
# here the moment it becomes a config/data table — and give it section
# banners in the same commit.
CONFIG_FILES: list[str] = []

BANNER_RE = re.compile(r"#.*═{5,}")


def _banner_lines(source: str) -> list[int]:
    return [i + 1 for i, line in enumerate(source.splitlines()) if BANNER_RE.search(line)]


def _top_level_table_names(tree: ast.Module) -> set[str]:
    names = set()
    for node in tree.body:
        if isinstance(node, ast.Assign):
            names.update(t.id for t in node.targets if isinstance(t, ast.Name))
        elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name):
            names.add(node.target.id)
    return names


def _is_boilerplate(node: ast.stmt) -> bool:
    if isinstance(node, (ast.Import, ast.ImportFrom)):
        return True
    if isinstance(node, ast.Expr) and isinstance(getattr(node, "value", None), ast.Constant):
        return True
    return False


def _duplicate_dict_keys(tree: ast.Module) -> list[tuple[int, str]]:
    found = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Dict):
            continue
        seen: set[object] = set()
        for key in node.keys:
            if isinstance(key, ast.Constant):
                if key.value in seen:
                    found.append((key.lineno, repr(key.value)))
                seen.add(key.value)
    return found


def _check_file(path: Path) -> list[str]:
    problems = []
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source, filename=str(path))
    banners = _banner_lines(source)
    table_names = _top_level_table_names(tree)
    rel = path.relative_to(PROJECT_ROOT).as_posix()

    for node in tree.body:
        if _is_boilerplate(node):
            continue
        if not any(b <= node.lineno for b in banners):
            problems.append(f"{rel}:{node.lineno}: top-level definition outside any section banner")
        if isinstance(node, ast.Assign):
            for t in node.targets:
                if isinstance(t, ast.Subscript) and isinstance(t.value, ast.Name) \
                        and t.value.id in table_names:
                    problems.append(
                        f"{rel}:{node.lineno}: post-definition patch of {t.value.id!r}"
                    )
        if isinstance(node, ast.Expr) and isinstance(node.value, ast.Call):
            func = node.value.func
            if isinstance(func, ast.Attribute) and func.attr == "update" \
                    and isinstance(func.value, ast.Name) and func.value.id in table_names:
                problems.append(
                    f"{rel}:{node.lineno}: post-definition patch of {func.value.id!r} (.update)"
                )

    for lineno, key in _duplicate_dict_keys(tree):
        problems.append(f"{rel}:{lineno}: duplicate dict key {key}")
    return problems


def test_config_files_obey_the_config_section_law():
    problems = []
    for rel in CONFIG_FILES:
        path = PROJECT_ROOT / rel
        assert path.exists(), f"CONFIG_FILES entry does not exist: {rel}"
        problems.extend(_check_file(path))
    assert not problems, "THE CONFIG SECTION LAW violated:\n" + "\n".join(problems)


if __name__ == "__main__":
    test_config_files_obey_the_config_section_law()
    print("PASS — test_config_sections")
