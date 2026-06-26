# Contributing to FuseMind

Thanks for helping build the universal watch bridge. The most valuable
contribution is **a new watch adapter** — but bug fixes, docs, and tests are all
welcome.

## Ground rules

- Be respectful. Assume good intent.
- One logical change per pull request.
- Every PR must keep CI green.
- Discuss large changes in an issue first.

## Branch & commit

```bash
git checkout -b feat/short-description
# ... make changes ...
git commit -m "feat(scope): concise description"
git push origin feat/short-description
```

Commit prefixes: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`.

## Adding a new watch platform (an adapter)

The whole point of FuseMind is that this is easy. The core never changes.

1. **Read the contract.** [`docs/ble-protocol.md`](docs/ble-protocol.md) is the
   exact GATT service, characteristics, and JSON payloads every watch speaks.
2. **Create the folder.** `watch-adapters/<your-platform>/`.
3. **Implement the watch-side contract** in your platform's language:
   - Wear OS → Kotlin (`WatchSideAdapter`)
   - Garmin → Monkey C
   - Amazfit → Zepp OS / JavaScript
   The required surface: start/stop GATT server, receive notifications, dismiss,
   read health, report capabilities.
4. **Report capabilities honestly.** If the watch has no speaker, set
   `hasSpeaker = false` — FuseMind degrades gracefully (no broken call audio).
5. **Pass the adapter contract tests.** Your adapter must satisfy the shared
   contract test suite — same tests, every platform.
6. **Open a PR.** A maintainer reviews against the contract and merges.

Your adapter then ships for all users in the next release.

## Definition of Done (every PR)

- [ ] Code self-reviewed — no TODOs left, no commented-out blocks
- [ ] Tests written/updated; coverage not reduced
- [ ] For adapters: contract tests pass
- [ ] CI green
- [ ] Docs updated if behaviour changed

## Security

Never commit secrets. API keys and tokens go in your local `.env` (which is
git-ignored). Use `.env.example` as the template. Report security issues
privately rather than in a public issue.
