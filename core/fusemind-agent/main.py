"""
FuseMind AI Agent — service skeleton (Phase 0).

The "brain" that the iPhone calls for intelligence:
  - POST /classify        notification -> priority H/M/L          (Phase 4)
  - POST /health/insight  watch + whoop data -> daily insight     (Phase 5)
  - POST /voice/intent    transcript -> structured action         (Phase 7)

Phase 0 ships the structure + stub responses so the iOS app can integrate
against a stable API. Real Groq/LLM calls land in Phases 4, 5, 7 where marked.

Run locally:
    pip install -r requirements.txt
    uvicorn main:app --reload --port 8000

Config comes from environment (.env), never hard-coded. See .env.example.

Apache-2.0 (c) FuseMind contributors
"""

from __future__ import annotations

import os
from enum import Enum
from typing import Optional

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="FuseMind Agent", version="0.1.0")

# Config is read from the environment. Do NOT commit real keys.
GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
GROQ_MODEL = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")
WHISPER_MODEL = os.getenv("GROQ_WHISPER_MODEL", "whisper-large-v3")


# ---------- Models ----------

class Priority(str, Enum):
    high = "H"
    medium = "M"
    low = "L"


class NotificationIn(BaseModel):
    app_name: str
    title: str
    body: str


class ClassifyOut(BaseModel):
    priority: Priority


class HealthIn(BaseModel):
    watch_data: dict
    whoop_data: Optional[dict] = None


class InsightOut(BaseModel):
    insight: str
    recommendation: str


class VoiceIn(BaseModel):
    transcript: str


class IntentOut(BaseModel):
    intent: str
    params: dict


# ---------- Health check ----------

@app.get("/")
def root():
    return {"service": "fusemind-agent", "version": app.version, "ok": True}


# ---------- Phase 4: notification classifier ----------

@app.post("/classify", response_model=ClassifyOut)
def classify(n: NotificationIn) -> ClassifyOut:
    """Score a notification's priority.

    TODO[Phase 4]: call Groq with a few-shot prompt that maps
    (app_name, title, body) -> H/M/L. For now return a simple heuristic so
    the iOS app can integrate end-to-end.
    """
    text = f"{n.app_name} {n.title} {n.body}".lower()
    urgent = ("call", "urgent", "now", "asap", "code", "otp", "verify")
    if any(w in text for w in urgent):
        return ClassifyOut(priority=Priority.high)
    if n.app_name.lower() in {"whatsapp", "messages", "signal", "telegram"}:
        return ClassifyOut(priority=Priority.medium)
    return ClassifyOut(priority=Priority.low)


# ---------- Phase 5: health insight ----------

@app.post("/health/insight", response_model=InsightOut)
def health_insight(h: HealthIn) -> InsightOut:
    """Fuse watch + Whoop data into a daily readiness insight.

    TODO[Phase 5]: send fused metrics to Groq for a natural-language insight.
    Stub returns a deterministic placeholder.
    """
    hrv = h.watch_data.get("hrv")
    recovery = (h.whoop_data or {}).get("recovery_score")
    if recovery is not None and recovery < 50:
        return InsightOut(
            insight="Recovery is low today.",
            recommendation="Keep it light — prioritise rest and hydration.",
        )
    return InsightOut(
        insight="You look ready for the day.",
        recommendation="A normal training load is fine.",
    )


# ---------- Phase 7: voice intent ----------

@app.post("/voice/intent", response_model=IntentOut)
def voice_intent(v: VoiceIn) -> IntentOut:
    """Parse a spoken command into a structured action.

    TODO[Phase 7]: Groq LLM intent parsing over the supported action set.
    Stub recognises a couple of patterns to prove the pipeline.
    """
    t = v.transcript.lower()
    if "timer" in t:
        return IntentOut(intent="set_timer", params={"raw": v.transcript})
    if "remind" in t:
        return IntentOut(intent="create_reminder", params={"raw": v.transcript})
    return IntentOut(intent="unknown", params={"raw": v.transcript})
