# Local Setup

What you install once on your development machine. Splits cleanly into what
runs anywhere vs what needs your Mac.

## Runs on any machine (the AI agent)
```bash
cd core/fusemind-agent
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp ../../.env.example ../../.env     # then fill in keys locally
uvicorn main:app --reload --port 8000
# visit http://localhost:8000  -> {"service":"fusemind-agent",...}
```

## Needs your Mac (the iPhone app) — Phase 1+
- Xcode (free from the App Store)
- A free Apple ID for on-device installs
- Open `core/fusemind-ios` in Xcode, plug in your iPhone, Run

## Needs Android tooling (the watch app) — Phase 1+
- Android Studio (free) with Wear OS SDK
- `adb` for installing to a real watch in developer mode
- Open `watch-adapters/wear-os` in Android Studio

## Accounts (all free tiers)
- GitHub — code + CI
- Groq — LLM + Whisper (Phase 4+)
- Supabase — persistence (Phase 2+)
- Whoop developer — health (Phase 5)

Keys go in `.env` (git-ignored). Never paste them into commits or chat.
