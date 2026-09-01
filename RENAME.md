# RENAME → Witness

Owner's pick, 2026-09-01 (session dc4cca6f): the project's name is **Witness**.
This session could not execute the rename — it was LIVE inside the folder, and
`rename_project.py` rightly refuses to run then (START.md → Renaming). Per that
rule this file carries the whole job; the session that executes it deletes it
in the same commit that finishes the rename.

## The name story (goes into README, same commit)

> **Why "Witness"** — because that is what a victim needs most and has least:
> someone who was there, remembers every word, and cannot be intimidated. The
> app is the witness in your pocket — it hears, writes down, and keeps the
> proof safe until you need it.

Note the disguise feature is UNCHANGED by the name: "Witness" is the store
identity; on the phone the user picks a neutral name and icon (see
`docs/FEATURES.md` → `disguise`).

## The command

```bash
python rules/tools/rename_project.py "Applications/Safety" "Applications/Witness" --dry-run
python rules/tools/rename_project.py "Applications/Safety" "Applications/Witness"
```

Review every hit the dry run shows BEFORE the real run. No `--force`, ever.

## What the tool will catch

- The folder move + session-history move (transcripts are keyed off the path).
- Whole-phrase "Safety" and path forms (`Applications/Safety`,
  `Applications\Safety`, `Safety.svg` references) across the monorepo docs:
  this project's README/CLAUDE/docs, root `PROJECTS.md` entry, root
  `README.md` table line.

## What it will NOT catch — do by hand, same commit

1. **The common noun trap.** "safety" is ordinary English all over this
   project's prose ("victim-safety apps", "safety use case", "personal-safety
   apps"). The tool only replaces the whole NAME phrase — leave the prose
   alone; do not mass-replace by hand either.
2. **The lowercase anchor** `<a id="safety"></a>` in root `PROJECTS.md` →
   `<a id="witness"></a>` (the tool's phrase forms may miss it; it is
   lowercase).
3. **`logos/Safety.svg`** → `git mv logos/Safety.svg logos/Witness.svg` in the
   ROOT repo, plus the two `<img src="logos/Safety.svg">` references
   (PROJECTS.md, root README) if the sweep leaves either behind.
4. **README name story**: replace the "working title, final name pending"
   line with the story above; drop the pending-name lines from CLAUDE.md
   "Open items" and docs/PLAN.md "Open".
5. **Icon Forge manifest** — check whether Safety was registered there; update
   if so.
6. **End with the case-insensitive sweep** (START.md): grep `-i` for
   `safety` spaced/hyphenated/underscored/joined, judging each hit as NAME vs
   ordinary English. Then add the fail-closed guard `tests/test_old_name.py`
   (the VibeCoder/WatchAcademy shape) asserting no NAME-usage of "Safety"
   survives — with the prose words whitelisted.

## Traps for the FUTURE (not part of this rename, decide at M1)

- **`applicationId` is forever on Play and visible to an inspecting abuser**
  (Settings → Apps → app details shows the package id). Choose a NEUTRAL
  package id from day one — not `com.uvuruna.witness` — e.g. something that
  reads like a utility. This must be decided before the first Play upload;
  it can never change after.
- No GitHub repo, no keystore, no installer, no Task Scheduler entries exist
  yet — nothing outside the monorepo carries the old name.
