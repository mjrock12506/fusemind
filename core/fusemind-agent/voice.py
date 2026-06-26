"""Voice intent parsing (Phase 7).

Turns a spoken command (already transcribed) into a structured action the iPhone
can execute. LLM-first over a fixed intent set; heuristic fallback covers the
common cases so the pipeline always returns something usable."""

from __future__ import annotations

import re

import llm

# The action set the iPhone knows how to execute.
INTENTS = [
    "set_timer", "set_alarm", "create_reminder", "send_message", "make_call",
    "play_music", "pause_music", "next_track", "weather", "search",
    "navigate", "check_calendar", "take_note", "unknown",
]

_SYSTEM = (
    "You convert a spoken command into a structured action for a phone "
    "assistant. Choose exactly one intent from this list:\n"
    f"{', '.join(INTENTS)}.\n"
    "Extract any relevant parameters (duration, time, contact, query, "
    "destination, text). "
    'Reply ONLY as JSON: {"intent": "<one of the list>", "params": {...}}. '
    'If nothing fits, use {"intent": "unknown", "params": {}}.'
)


def _heuristic(transcript: str) -> tuple[str, dict]:
    t = transcript.lower()
    if "timer" in t:
        return "set_timer", {"raw": transcript}
    if "alarm" in t:
        return "set_alarm", {"raw": transcript}
    if "remind" in t:
        return "create_reminder", {"raw": transcript}
    if "text" in t or "message" in t or "tell" in t:
        return "send_message", {"raw": transcript}
    if "call" in t:
        return "make_call", {"raw": transcript}
    if "play" in t:
        return "play_music", {"raw": transcript}
    if "pause" in t or "stop" in t:
        return "pause_music", {}
    if "weather" in t:
        return "weather", {"raw": transcript}
    if "navigate" in t or "directions" in t or "take me" in t:
        return "navigate", {"raw": transcript}
    if "remember" in t or "note" in t:
        return "take_note", {"raw": transcript}
    if re.search(r"\b(what'?s|when|where|who|how)\b", t):
        return "search", {"query": transcript}
    return "unknown", {"raw": transcript}


def parse_intent(transcript: str) -> tuple[str, dict, str]:
    """Return (intent, params, source)."""
    data = llm.chat_json(_SYSTEM, transcript, max_tokens=120)
    if data:
        intent = str(data.get("intent", "")).strip()
        if intent in INTENTS:
            params = data.get("params") or {}
            if isinstance(params, dict):
                return intent, params, "llm"
    i, p = _heuristic(transcript)
    return i, p, "fallback"
