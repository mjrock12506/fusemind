"""Thin Groq wrapper. Every call is defensive: if there's no API key, or the
network/API fails, or the response can't be parsed, callers get None and fall
back to a deterministic heuristic. The service never 500s on a flaky LLM."""

from __future__ import annotations

import json
import re
from typing import Optional

from config import settings

_client = None


def _get_client():
    """Lazily build the Groq client. Returns None when no key is configured."""
    global _client
    if _client is not None:
        return _client
    if not settings.llm_enabled:
        return None
    try:
        from groq import Groq  # imported lazily so the package isn't required offline
        _client = Groq(api_key=settings.groq_api_key)
    except Exception:
        _client = None
    return _client


def chat(system: str, user: str, max_tokens: int = 256) -> Optional[str]:
    """Single-shot chat completion. Returns the raw text, or None on any failure."""
    client = _get_client()
    if client is None:
        return None
    try:
        resp = client.chat.completions.create(
            model=settings.groq_model,
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            temperature=0,
            max_tokens=max_tokens,
        )
        return resp.choices[0].message.content
    except Exception:
        return None


def chat_json(system: str, user: str, max_tokens: int = 256) -> Optional[dict]:
    """Chat completion expected to return JSON. Returns a dict or None."""
    text = chat(system, user, max_tokens)
    return parse_json(text)


def parse_json(text: Optional[str]) -> Optional[dict]:
    """Tolerant JSON extraction: strips ``` fences, then falls back to the first
    {...} block. Returns None if nothing parses."""
    if not text:
        return None
    cleaned = re.sub(r"^```(?:json)?|```$", "", text.strip(), flags=re.MULTILINE).strip()
    try:
        return json.loads(cleaned)
    except Exception:
        pass
    match = re.search(r"\{.*\}", cleaned, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(0))
        except Exception:
            return None
    return None


def transcribe(audio_bytes: bytes, filename: str = "audio.wav") -> Optional[str]:
    """Groq Whisper STT. Returns transcript text, or None offline / on failure."""
    client = _get_client()
    if client is None:
        return None
    try:
        resp = client.audio.transcriptions.create(
            model=settings.whisper_model,
            file=(filename, audio_bytes),
        )
        return resp.text
    except Exception:
        return None
