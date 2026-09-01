# tests/

The project's guard tests — installed at founding (2026-09-01), before any
product code, per the monorepo's START rules. Run by the hooks through
`run_guards.py` (script); no test module here carries its own `__about` doc —
this file covers the folder.

## Files

| File | What it guards |
|------|----------------|
| `run_guards.py` | Hook entry point: `--fast` (PostToolUse) runs structure + config guards; full (Stop) adds docs coverage, link chain, clone guard and rules-size guard. |
| `test_structure_law.py` | THE STRUCTURE LAW: no source file over ~1,000 lines without a ratchet entry; no log file in the project root. |
| `test_config_sections.py` | THE CONFIG SECTION LAW over `CONFIG_FILES` (empty until the first config/data table exists). |
| `test_docs_coverage.py` | MD-First tiers: every source file classified, every Standard/Algorithmic file has its `__about`/`__flow` doc. |
| `test_doc_links.py` | Navigation chain: every `.md` reachable from `README.md`, no broken relative link. |
| `_guards_common.py` | Shared walk helpers (excluded dirs, source extensions). |

## Connections

- Uses: `rules/hooks/changed_files.py`, `rules/tools/clone_guard.py`,
  `rules/tools/rules_size_guard.py` at the monorepo root (loaded by path;
  their absence never silently disables a law).
- Used by: `.claude/settings.json` hooks (PostToolUse fast pass, Stop full pass).
