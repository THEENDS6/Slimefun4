# Contributing to SlimeFunX

SlimeFunX is currently an experimental, fast-moving personal development line.

For now, the preferred contribution process is simple:

1. Contact THEENDS6 directly before large changes.
2. Describe the problem or feature clearly.
3. Include server version, PacketEvents version, and logs when reporting bugs.
4. Do not submit large rewrites without prior discussion.

## Local checks

Before sharing a change, at minimum run the project build in the full Gradle workspace:

```bash
./gradlew clean build
```

If the change affects runtime behavior, test it on a local Paper server before publishing it.

## Pull requests

Formal PR rules are not established yet. If PRs are accepted later, they should generally be small, focused, and limited to one topic per PR.

Good examples:

- fix one machine bug
- add one missing permission check
- update one content definition group
- refactor one service boundary

Bad examples:

- formatting plus logic changes in the same commit
- unrelated machine, storage, UI, and command changes in one PR
- large rewrites without explanation

## Code boundaries

Use `cc.theends6.sfx.api` for intended external-facing contracts.
Use `cc.theends6.sfx.internal` for implementation details.

Do not treat internal packages as stable API.
