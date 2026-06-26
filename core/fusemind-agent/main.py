"""
FuseMind AI Agent — the "brain" the iPhone calls for intelligence.

Endpoints
  GET  /                  health check
  POST /classify         notification -> priority H/M/L          (Phase 4)
  POST /health/insight   watch + whoop data -> daily insight     (Phase 5)
  POST /voice/transcribe audio file -> transcript (Whisper)      (Phase 7)
  POST /voice/intent     transcript -> structured action         (Phase 7)

Every endpoint is LLM-first with a deterministic fallback, so the service
works fully offline / in CI without a Groq key. Set GROQ_API_KEY in .env to
enable real Groq (LLaMA + Whisper) calls.

Run locally:
    pip install -r requirements.txt
    uvicorn main:app --reload --port 8000

Apache-2.0 (c) FuseMind contributors
"""

from __future__ import annotations

from fastapi import FastAPI, File, UploadFile

import classifier
import health
import llm
import voice
from config import settings
from models import (
    ClassifyOut,
    HealthIn,
    InsightOut,
    IntentOut,
    NotificationIn,
    TranscriptOut,
    VoiceIn,
)

app = FastAPI(title="FuseMind Agent", version="1.0.0")


@app.get("/")
def root() -> dict:
    return {
        "service": "fusemind-agent",
        "version": app.version,
        "llm_enabled": settings.llm_enabled,
        "model": settings.groq_model if settings.llm_enabled else None,
        "ok": True,
    }


@app.post("/classify", response_model=ClassifyOut)
def classify(n: NotificationIn) -> ClassifyOut:
    priority, source = classifier.classify(n.app_name, n.title, n.body)
    return ClassifyOut(priority=priority, source=source)


@app.post("/health/insight", response_model=InsightOut)
def health_insight(h: HealthIn) -> InsightOut:
    i, r, source = health.insight(h.watch_data, h.whoop_data)
    return InsightOut(insight=i, recommendation=r, source=source)


@app.post("/voice/transcribe", response_model=TranscriptOut)
async def voice_transcribe(file: UploadFile = File(...)) -> TranscriptOut:
    audio = await file.read()
    text = llm.transcribe(audio, file.filename or "audio.wav")
    if text is None:
        # Offline / no key: return empty transcript rather than erroring.
        return TranscriptOut(transcript="", source="fallback")
    return TranscriptOut(transcript=text, source="llm")


@app.post("/voice/intent", response_model=IntentOut)
def voice_intent(v: VoiceIn) -> IntentOut:
    intent, params, source = voice.parse_intent(v.transcript)
    return IntentOut(intent=intent, params=params, source=source)
