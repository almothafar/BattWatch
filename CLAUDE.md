# BattWatch — agent guide

Android app that monitors the device battery and notifies on low/critical/full levels, high temperature, and battery health. Single-module Gradle project (`app/`), Java, min SDK 26.

## Commit & PR title convention (required)

Every PR title **must** follow [Conventional Commits](https://www.conventionalcommits.org): `type: description`, lowercase, imperative, no trailing period.

```
feat: add banana counter to the details table
fix: radiation is missing from bananas
refactor: split NotificationService into channels / dispatch
```

Allowed types: `feat`, `fix`, `perf`, `refactor`, `docs`, `build`, `ci`, `chore`, `test`, `style`, `revert`. `feat` and `fix` drive version bumps; the rest land in history without one. A breaking change is marked `feat!:` / `fix!:` or a `BREAKING CHANGE:` footer.

PRs are **squash-merged**, so the PR title becomes the commit on `master` — that is the text release-please reads. A CI check (`PR Title`) blocks non-conforming titles. Individual commit messages on a branch don't matter; the PR title is what counts.

## Versioning — do not hand-edit

The version lives in **`.release-please-manifest.json`** and nowhere else. `app/build.gradle` reads that one number and derives both `versionName` and `versionCode` from it, so the two always move together and there is never a commit hash in the version.

- `versionName` is the manifest version verbatim, e.g. `3.1.55`.
- `versionCode = MAJOR*100000 + MINOR*1000 + PATCH`, so the name and the number line up: `3.0.1` → `300001`, `3.1.55` → `301055`.

**release-please** owns the number. Merging normal PRs makes it keep a standing "Release" PR that bumps the manifest + `CHANGELOG.md` from the Conventional Commit titles (`feat` → minor, `fix` → patch, breaking → major). Merging that Release PR is what cuts a version. Never bump a version by editing a file yourself.

Building the upload APK is manual (from a local machine) — the pipeline only decides the number and records what changed; it does not publish the APK.

## Reference

The coding standards are imported below, so they load with this file on every session — line width, no `final` on parameters, exception handling, the Western-digits rule, and the ban on tooling metadata in commits and PR bodies:

@.claude/guidelines.md

Read on demand, when the work touches them:

- `.claude/guidelines/android.md` — API-level branches, deprecation policy, reflection ban, UI, threading, accessibility, Gradle/ProGuard.
- `.claude/guidelines/testing.md` — JUnit vs. Robolectric and what to cover.
- `.claude/guidelines/patterns.md` — house patterns, JavaDoc format, architectural decision log.
- `CONTEXT.md` — glossary of domain terms (drain rate, charge rate, design capacity, …). Use these exact terms in UI and code.
- `CODE_REVIEW_GUIDELINES.md` — the human-facing edition of the same standards, plus the reviewer checklist.

Every user-facing string needs an Arabic translation — the build fails on `MissingTranslation`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
