"""Stop hook — THE HALF-RECORDING BAN (owner decree 2026-09-11).

The owner's rule, in his words: never again deliver a solution whose answer to
"is the other party heard in the recording?" is NO.

A recording that holds only the victim's own voice is not evidence. It proves
nothing, it protects nobody, and offering it as a result — in a table, a plan,
a report or a built feature — wastes the one thing the user came for. So this
hook reads the assistant's final message of the turn and REFUSES to let the
turn end while that claim is being handed over as an outcome.

Discussing WHY a path fails is allowed only when the same message also carries
a path that does capture both sides (the escape marker below). A message that
only explains the failure is exactly what the decree forbids.

Exit codes: 0 = let the turn end · 2 = block, stderr goes back to Claude.
"""

from __future__ import annotations

import json
import re
import sys
import unicodedata

# A message may state the limitation ONLY if it also delivers a working path.
# Writing this marker is a claim that a both-sides solution is in the message.
ESCAPE = "obe-strane-resenje:"

# Each entry: (human-readable name, regex over the de-accented lowercase text).
BANNED = [
    (
        "the other party is not in the recording",
        r"(sagovornik\w*|drug\w+ stran\w+|far side|other party|callee)"
        r"[^.!?\n]{0,120}"
        r"(\bne\b|nece|necemo|nemoguc\w*|not\b|never\b|no\b|tisin\w*|silence)",
    ),
    (
        "only the user's own voice is captured",
        r"(samo (moj|svoj|korisnikov|njen|njegov)\w* glas|only (my|the user'?s|your) (own )?voice"
        r"|own voice only|samo sebe (cuje|snima))",
    ),
    (
        "an ordinary app receives silence during a call",
        r"(obicn\w+ aplikacij\w+|ordinary apps?|third-party apps?)"
        r"[^.!?\n]{0,80}"
        r"(tisin\w*|silence|silenced)",
    ),
    (
        "a NO/NO verdict row for hearing the other side",
        r"sagovornik[^|\n]*\|[^|\n]*\bne\b[^|\n]*\|[^|\n]*\bne\b",
    ),
]


def flatten(text: str) -> str:
    """Lowercase and strip diacritics so 'NE'/'ne', 'čuje'/'cuje' match alike."""
    decomposed = unicodedata.normalize("NFD", text.lower())
    stripped = "".join(c for c in decomposed if unicodedata.category(c) != "Mn")
    return stripped.replace("đ", "d").replace("**", "").replace("*", "")


def final_assistant_text(transcript_path: str) -> str:
    """The text blocks of the LAST assistant message in the transcript."""
    try:
        with open(transcript_path, encoding="utf-8") as fh:
            lines = [json.loads(ln) for ln in fh if ln.strip()]
    except (OSError, json.JSONDecodeError):
        return ""

    for entry in reversed(lines):
        message = entry.get("message") or {}
        if entry.get("type") != "assistant" and message.get("role") != "assistant":
            continue
        content = message.get("content") or []
        if isinstance(content, str):
            return content
        parts = [c.get("text", "") for c in content if isinstance(c, dict) and c.get("type") == "text"]
        if any(p.strip() for p in parts):
            return "\n".join(parts)
    return ""


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0  # never block on a malformed payload

    if payload.get("stop_hook_active"):
        return 0  # already looping on a stop hook; do not deadlock the session

    text = final_assistant_text(payload.get("transcript_path", ""))
    if not text:
        return 0

    flat = flatten(text)
    if ESCAPE in flat:
        return 0

    hits = [name for name, pattern in BANNED if re.search(pattern, flat)]
    if not hits:
        return 0

    print(
        "THE HALF-RECORDING BAN (owner decree 2026-09-11) — this message hands over "
        "a result in which the other party is NOT in the recording:\n  - "
        + "\n  - ".join(hits)
        + "\n\nA recording with only the user's own voice is not evidence, and the owner "
        "has forbidden delivering it. Rewrite the message so it delivers a path that "
        "captures BOTH sides (the call carried by the app itself is one), and mark that "
        "message with the literal marker '" + ESCAPE + "' to confirm such a path is in it. "
        "Do not weaken the claim to get past this hook — deliver the working path.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
