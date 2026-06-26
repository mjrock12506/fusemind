"""Notification priority classifier (Phase 4).

LLM-first: a few-shot prompt asks Groq to label H/M/L. If the LLM is
unavailable or returns something invalid, fall back to a transparent heuristic
so behaviour is always defined and CI is deterministic."""

from __future__ import annotations

import llm

_SYSTEM = (
    "You triage smartwatch notifications. Classify how urgently the wearer "
    "should be interrupted:\n"
    "  H = high: needs attention now (incoming calls, OTP/2FA codes, messages "
    "from real people, time-critical alerts, security warnings).\n"
    "  M = medium: worth a glance soon (routine app messages, reminders, "
    "delivery updates).\n"
    "  L = low: can wait (promotions, newsletters, social likes, marketing).\n"
    'Reply with ONLY a JSON object: {"priority": "H"} (or M or L). No prose.'
)

_MESSAGING_APPS = {
    "whatsapp", "messages", "imessage", "signal", "telegram",
    "messenger", "wechat", "line", "slack",
}
_URGENT_WORDS = (
    "urgent", "asap", "now", "immediately", "emergency", "code", "otp",
    "verify", "verification", "security", "alert", "password", "expire",
    "deadline", "call me",
)
_LOW_APPS = {
    "news", "promotions", "shopping", "deals", "newsletter", "ads",
    "instagram", "facebook", "tiktok", "x", "twitter",
}


def _heuristic(app_name: str, title: str, body: str) -> str:
    text = f"{app_name} {title} {body}".lower()
    app = app_name.strip().lower()
    if any(w in text for w in _URGENT_WORDS):
        return "H"
    if app in _MESSAGING_APPS:
        return "M"
    if app in _LOW_APPS:
        return "L"
    return "M"


def classify(app_name: str, title: str, body: str) -> tuple[str, str]:
    """Return (priority, source) where source is 'llm' or 'fallback'."""
    data = llm.chat_json(_SYSTEM, f"App: {app_name}\nTitle: {title}\nBody: {body}", max_tokens=20)
    if data:
        p = str(data.get("priority", "")).strip().upper()
        if p in {"H", "M", "L"}:
            return p, "llm"
    return _heuristic(app_name, title, body), "fallback"
