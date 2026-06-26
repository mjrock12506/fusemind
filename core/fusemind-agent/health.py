"""Health insight fusion (Phase 5).

Combines watch metrics (HR, HRV, steps) with optional Whoop recovery/strain
into a short, calm daily insight + one-line recommendation. LLM-first with a
deterministic fallback. Tone is intentionally non-alarming."""

from __future__ import annotations

import json

import llm

_SYSTEM = (
    "You are a calm, factual health companion. Given today's watch metrics and "
    "optional Whoop data, write a short daily insight and a one-line "
    "recommendation. Never alarm the user or imply a diagnosis. "
    'Reply ONLY as JSON: {"insight": "...", "recommendation": "..."}. '
    "Keep each field under 140 characters."
)


def _heuristic(watch_data: dict, whoop_data: dict | None) -> tuple[str, str]:
    recovery = (whoop_data or {}).get("recovery_score")
    hrv = watch_data.get("hrv")
    if recovery is not None and recovery < 50:
        return ("Recovery is on the lower side today.",
                "Keep activity light, hydrate, and prioritise sleep tonight.")
    if recovery is not None and recovery >= 75:
        return ("Recovery looks strong today.",
                "A solid training session is well within reach.")
    if hrv is not None and hrv < 30:
        return ("Heart-rate variability is a little low.",
                "An easier day may help you bounce back.")
    return ("Your metrics look steady today.",
            "A normal day of activity is fine.")


def insight(watch_data: dict, whoop_data: dict | None) -> tuple[str, str, str]:
    """Return (insight, recommendation, source)."""
    payload = json.dumps({"watch": watch_data, "whoop": whoop_data})
    data = llm.chat_json(_SYSTEM, payload, max_tokens=160)
    if data and data.get("insight") and data.get("recommendation"):
        return str(data["insight"]), str(data["recommendation"]), "llm"
    i, r = _heuristic(watch_data, whoop_data)
    return i, r, "fallback"
