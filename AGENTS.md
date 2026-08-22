# BattWatch — agent entry point

This file exists so agents that follow the `AGENTS.md` convention find their way in. It deliberately carries no rules of its own: everything lives in [`CLAUDE.md`](CLAUDE.md), and a second copy here would drift out of sync with it within a release or two.

**Read [`CLAUDE.md`](CLAUDE.md) first.** It covers what the app is, the Conventional Commit PR-title requirement, the versioning rules (release-please owns the number — never hand-edit a version), the graphify knowledge graph, and the Arabic translation gate. It imports [`.claude/guidelines.md`](.claude/guidelines.md), the machine-facing coding standards, which you should treat as loaded whenever you edit code.

Read on demand, when the work touches them:

- [`.claude/guidelines/android.md`](.claude/guidelines/android.md) — API-level branches, deprecation policy, reflection ban, UI, threading, accessibility, Gradle/ProGuard.
- [`.claude/guidelines/testing.md`](.claude/guidelines/testing.md) — JUnit vs. Robolectric and what to cover.
- [`.claude/guidelines/patterns.md`](.claude/guidelines/patterns.md) — house patterns, JavaDoc format, architectural decision log.
- [`CONTEXT.md`](CONTEXT.md) — glossary of domain terms (drain rate, charge rate, design capacity, …). Use these exact terms in UI and code.
- [`CODE_REVIEW_GUIDELINES.md`](CODE_REVIEW_GUIDELINES.md) — the human-facing edition of the same standards, plus the reviewer checklist.

`graphify-out/` is generated and git-ignored, so it will be absent on a fresh clone. Build it with the graphify skill when you want it; the rules in `CLAUDE.md` are written to apply only when `graphify-out/graph.json` is actually present.
