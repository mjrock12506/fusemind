"""Configuration for the FuseMind agent. Read from the environment (.env).
Never hard-code keys. If GROQ_API_KEY is unset the agent runs in fallback
mode (deterministic heuristics) so it still works offline and in CI."""

from __future__ import annotations

import os


class Settings:
    groq_api_key: str = os.getenv("GROQ_API_KEY", "")
    groq_model: str = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")
    whisper_model: str = os.getenv("GROQ_WHISPER_MODEL", "whisper-large-v3")

    @property
    def llm_enabled(self) -> bool:
        """True when a Groq key is present, so real LLM calls are made."""
        return bool(self.groq_api_key)


settings = Settings()
