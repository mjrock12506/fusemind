"""Tests exercise the deterministic fallback path (no GROQ_API_KEY), which is
exactly what CI runs. With a key set, the LLM path is used instead; these
guarantees still hold because the fallback defines the contract."""

from fastapi.testclient import TestClient

import classifier
import health
import voice
from main import app

client = TestClient(app)


# ---- classifier ----

def test_classify_urgent_is_high():
    assert classifier.classify("Bank", "Code", "Your OTP is 4821")[0] == "H"

def test_classify_messaging_is_medium():
    assert classifier.classify("WhatsApp", "Sara", "on my way")[0] == "M"

def test_classify_marketing_is_low():
    assert classifier.classify("Shopping", "Sale", "50% off today")[0] == "L"


# ---- health ----

def test_health_low_recovery_is_gentle():
    i, r, _ = health.insight({"hrv": 40}, {"recovery_score": 35})
    assert "light" in r.lower() or "rest" in r.lower()

def test_health_high_recovery_is_encouraging():
    i, r, _ = health.insight({"hrv": 70}, {"recovery_score": 85})
    assert "training" in r.lower() or "session" in r.lower()


# ---- voice ----

def test_voice_timer():
    assert voice.parse_intent("set a timer for ten minutes")[0] == "set_timer"

def test_voice_reminder():
    assert voice.parse_intent("remind me to call mum")[0] == "create_reminder"

def test_voice_unknown_is_safe():
    intent, params, _ = voice.parse_intent("zxcv qwerty")
    assert intent in voice.INTENTS


# ---- API shape ----

def test_root_ok():
    body = client.get("/").json()
    assert body["ok"] is True and body["service"] == "fusemind-agent"

def test_classify_endpoint():
    r = client.post("/classify", json={"app_name": "Phone", "title": "Dad", "body": "call me now"})
    assert r.status_code == 200 and r.json()["priority"] == "H"

def test_health_endpoint():
    r = client.post("/health/insight", json={"watch_data": {"hrv": 35}, "whoop_data": {"recovery_score": 40}})
    assert r.status_code == 200 and r.json()["recommendation"]

def test_voice_intent_endpoint():
    r = client.post("/voice/intent", json={"transcript": "set a timer for 5 minutes"})
    assert r.status_code == 200 and r.json()["intent"] == "set_timer"
