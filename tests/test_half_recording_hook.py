"""The half-recording ban must actually fire (owner decree 2026-09-11).

`tests/hook_no_half_recording.py` is the tooth behind the decree: a turn may
not end while its final message hands over a result in which the other party is
missing from the recording. A hook nobody tests is a promise, not a guard — so
these cases pin both directions: the forbidden claim blocks, a real both-sides
solution passes.

Clarified 2026-09-14 (owner): "both voices" counts two PEOPLE, not one audio
path — a recording made through the microphone with the speaker on satisfies
the decree, so a message offering that route must pass.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

import pytest

HOOK = Path(__file__).resolve().parent.parent / "tests" / "hook_no_half_recording.py"

BLOCKED = [
    pytest.param("| Da se u snimku cuje SAGOVORNIK | **NE** | **NE** |", id="verdict-table-row"),
    pytest.param("Sagovornik se nece cuti u snimku.", id="prose-serbian"),
    pytest.param("The far side will not be captured by an ordinary app.", id="prose-english"),
    pytest.param("Obicna aplikacija dobija tisinu tokom poziva.", id="ordinary-app-silence"),
    pytest.param("U fajlu ostaje samo korisnikov glas.", id="own-voice-only"),
    pytest.param("Druga strana nije u snimku, to Android ne daje.", id="other-side-absent"),
]

ALLOWED = [
    pytest.param(
        "Poziv ide kroz aplikaciju, pa snimamo obe strane u punom kvalitetu na svakom telefonu.",
        id="both-sides-solution",
    ),
    pytest.param(
        "Sagovornik se ne cuje preko sistemskog Telefona. "
        "obe-strane-resenje: poziv kroz aplikaciju snima obe strane.",
        id="limitation-plus-escape-marker",
    ),
    pytest.param(
        "Na ovom telefonu tihi put daje samo jedan glas. "
        "obe-strane-resenje: spikerfon ukljucen, mikrofon snima obe osobe u istom fajlu.",
        id="speakerphone-counts-as-both-people",
    ),
    pytest.param("Whitelist i data sekcija su gotove, transkript radi.", id="unrelated-progress"),
]


def run_hook(tmp_path: Path, message: str, *, stop_hook_active: bool = False) -> int:
    transcript = tmp_path / "transcript.jsonl"
    transcript.write_text(
        json.dumps(
            {"type": "assistant", "message": {"role": "assistant", "content": [{"type": "text", "text": message}]}}
        )
        + "\n",
        encoding="utf-8",
    )
    payload = {"transcript_path": str(transcript), "stop_hook_active": stop_hook_active}
    proc = subprocess.run(
        [sys.executable, str(HOOK)], input=json.dumps(payload), capture_output=True, text=True
    )
    return proc.returncode


@pytest.mark.parametrize("message", BLOCKED)
def test_half_recording_claim_blocks_the_turn(tmp_path: Path, message: str) -> None:
    assert run_hook(tmp_path, message) == 2, f"the ban let this through: {message!r}"


@pytest.mark.parametrize("message", ALLOWED)
def test_real_solution_passes(tmp_path: Path, message: str) -> None:
    assert run_hook(tmp_path, message) == 0, f"the ban blocked a legitimate message: {message!r}"


def test_never_deadlocks_on_its_own_block(tmp_path: Path) -> None:
    """A second pass with stop_hook_active must let the session out."""
    assert run_hook(tmp_path, "Sagovornik se nece cuti.", stop_hook_active=True) == 0
