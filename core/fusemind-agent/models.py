"""Request/response models, shared with the iOS app's expectations.
Mirrors the types in WatchAdapter.swift and docs/ble-protocol.md."""

from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel


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
    source: str  # "llm" or "fallback" — so the client knows how it was decided


class HealthIn(BaseModel):
    watch_data: dict
    whoop_data: Optional[dict] = None


class InsightOut(BaseModel):
    insight: str
    recommendation: str
    source: str


class TranscriptOut(BaseModel):
    transcript: str
    source: str


class VoiceIn(BaseModel):
    transcript: str


class IntentOut(BaseModel):
    intent: str
    params: dict
    source: str
