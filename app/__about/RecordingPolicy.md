# RecordingPolicy

The one place that decides whether a call is recorded.

## Responsibility

Answer a single question — record this call or not — and say WHY, so the app can
explain itself to the user instead of behaving mysteriously.

## Decisions that outlive the code

- **One class, one answer.** The call monitor, the settings preview and the tests
  all ask this class. Nothing duplicates the reasoning; a second copy of this
  logic anywhere is a bug by definition (ONE KIND, ONE CLASS).
- **The order is deliberate, and the user's explicit choice always wins:** an
  explicit rule for the number, then the rule for callers who are not in the
  phone's contacts, then the default. A whitelist entry can never be overridden
  by a default, and a blacklist entry can never be overridden by anything.
- **The shipped default is RECORD EVERYTHING.** A victim must not lose the one
  call that mattered to a list she forgot to update. The opposite default would
  be tidier and would cost someone their evidence.
- **A withheld number still gets an answer** (`Reason.NO_NUMBER`) rather than an
  exception — the call is real even when the number is not.

## Connections

- Reads: `data/NumberRuleDao` (the two lists), `data/Prefs` (the two defaults).
- Used by: the capture pipeline before a recording starts, and the Numbers screen
  to show the user what her settings currently mean.
