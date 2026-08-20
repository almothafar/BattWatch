# BattWatch Development Guidelines

> **Audience:** this is the machine-facing rulebook, loaded into every session via the `@` import in [`../CLAUDE.md`](../CLAUDE.md). The human-facing companion is [`../CODE_REVIEW_GUIDELINES.md`](../CODE_REVIEW_GUIDELINES.md); keep the two in sync when a standard changes.

## Project Overview

BattWatch (formerly Simple Battery Notifier) is an Android application that monitors battery status and provides notifications for power events. The app displays battery information with a circular progress bar and supports customizable alerts. Single Gradle module (`app/`), Java, min SDK 26.

The Java package and `applicationId` are still `com.almothafar.simplebatterynotifier`. That is deliberate — the `applicationId` is the app's identity on Play, and changing it would orphan every installed copy. Rebrand user-facing strings and docs, never the package.

## Detail files

This file carries the rules that apply to any edit. The rest live alongside it and are read on demand:

- [`guidelines/android.md`](guidelines/android.md) — API-level branches, deprecation policy, reflection ban, UI components, threading, accessibility/RTL, permissions & storage, performance, Gradle/ProGuard.
- [`guidelines/testing.md`](guidelines/testing.md) — JUnit vs. Robolectric, what to cover, the `@Config(sdk = 34)` constraint.
- [`guidelines/patterns.md`](guidelines/patterns.md) — house patterns to copy (builder, null-safety, switch expressions, resource cleanup), JavaDoc & comment format, architectural decision log.

## Code Style

### Java Language Features
- **Use modern Java (JDK 25) features** where appropriate
  - Switch expressions with `yield` keyword
  - Pattern matching (when available)
  - Records for simple data carriers
- **Resource Management**: Use try-with-resources for automatic resource cleanup
- **Null Safety**: Use `isNull()` and `nonNull()` from `java.util.Objects`
- **Immutability**: Use `final` for local variables where possible. Do **not** put `final` on method/constructor parameters in new or edited code — effectively-final already covers lambda capture, so the keyword is just noise. Legacy code carries `final` params (an older convention) and migrates incrementally as methods are otherwise touched; a full sweep isn't required.
- **Static Imports**: Import commonly used static methods (e.g., `isNull`, `nonNull`)

### Naming Conventions
- Classes: PascalCase (e.g., `BatteryDO`, `SystemService`)
- Methods: camelCase with clear verb prefixes (e.g., `getBatteryInfo`, `determineHealthStatus`)
- Constants: UPPER_SNAKE_CASE (e.g., `TAG`, `ANIMATION_DURATION`)
- Private fields: camelCase (e.g., `batteryDO`, `healthStatus`)

### Formatting & Wrapping
- **Max line width 160.** If it fits on one line, put it on one line — do not wrap needlessly. A wrapped signature that measures under 160 is a defect.
- **160 applies to comments and JavaDoc too.** Wrap comment prose at the same 160 as code; do not narrow it to ~100 because the surrounding paragraph looks tidy that way.
- **Markdown is never hard-wrapped.** In `.md` files, commit messages, PR bodies and issue bodies, each paragraph is one long line that the editor soft-wraps. Hard breaks inside a paragraph make every later edit re-flow the block, turning a one-word change into a multi-line diff.
- **More than 4 parameters → one per line**, even when the whole list would fit inside 160. Four or fewer stay on one line whenever they fit. Past four the eye can't count the arguments or spot a wrong-order one, so the count beats the width.
- **Chained calls (builders, streams): all on one line if they fit, otherwise one call per line.** Never a half-and-half split where some calls share a line and others don't — the break positions then carry no meaning.

### Code Organization
- Keep utility classes final with private constructors
- Group related methods together
- Order: constructors → public methods → protected methods → private methods → inner classes
- Maximum method length: ~30 lines (extract helper methods if longer)

## Error Handling

- Check for null before accessing system services or intent extras
- Use graceful degradation for non-critical features (e.g., battery-capacity estimate returning 0 when unsupported)
- Log warnings (not errors) for expected failures
- Add clear comments explaining why certain failures are acceptable
- **Never swallow exceptions silently and never `catch (Exception)` broadly.** A catch must take real recovery action or log. Catch the narrowest type.
- **Don't use exceptions for expected validation.** Unchecked exceptions like `NumberFormatException` can be avoided by validating first (e.g. `s.matches("\\d{1,5}")`) and then parsing without a `try`. A catch that shows the user feedback (e.g. `ActivityNotFoundException` → Toast) is fine.

### Input Validation
- Validate at system boundaries (user input, external APIs)
- Trust internal code and framework guarantees
- Don't add validation for scenarios that can't happen

## Code Quality

### Avoid Over-Engineering
- Only implement features that are directly requested
- Don't add "improvements" beyond the requirements
- Keep solutions simple and focused
- Three similar lines of code is better than a premature abstraction — but "premature" means the duplication is still hypothetical. Once the same shape is genuinely repeated, or the operation already has a counterpart helper it should mirror (a `post` implies a `cancel`), extracting it is not premature and "When to Extract Methods" below applies instead.

### When to Extract Methods
- Method exceeds ~30 lines
- Logic is duplicated in multiple places
- Complex algorithm that needs clear naming
- Side effect separation (separate mutation from computation)
- A shared utility or its symmetric counterpart already exists — the new code should join it rather than re-implement the same guard clauses

## Internationalization

### String Resources
- **All user-facing text must be in string resources — never a hardcoded Java literal** (no `setText("Excellent")`, no `Toast` string literals). Map values/enums to a `@StringRes` in a UI/service layer.
- Use positional format args for dynamic content: `<string name="notification_status_title">%1$s · %2$s</string>`. Always `%s`, never `%d` — see "Numbers Are Always Western Digits" below.

### Arabic (`values-ar/`) Parity
- The app ships an Arabic translation. Every new user-facing string in `values/strings.xml` needs a matching entry in `values-ar/strings.xml`.
- Quick check: diff the `<string name=…>` lists of the two files.
- Consider the `MissingTranslation` lint check as a build gate.
- Arabic wording is drafted by the AI agent and reviewed by the maintainer (native speaker) before merge.

### Resource Naming
- Prefix internal keys with underscore: `_pref_key_*`. Internal identifiers (`_pref_key_*`, `_pref_value_*`, `pref_category_*`, `extra_category`, URIs) are **not** translated — leave them out of `values-ar/`.
- Use descriptive names: `battery_health_good`, not `bh_g`

### Numbers Are Always Western Digits
- **Every number a user sees renders Western (`85`), never Eastern Arabic (`٨٥`), in any locale.** The words around a number are translated; the number is not. Product decision, enforced by `BatteryPercentFormatter` since #96.
- **`Locale.ROOT` for all numeric formatting** — persisted and user-facing alike. `Locale.getDefault()` has no numeric use in this app.
- **`getString(id, someInt)` is the trap.** `Resources.getString` formats with the **configuration** locale — the device's, not the one the string was declared in — so a `%1$d` placeholder emits Eastern digits on `ar-EG`/`ar-SA`/`ar-JO` with no `String.format` anywhere in your code. Declare the placeholder as `%1$s` and pass an already-formatted string — `BatteryPercentFormatter.formatWhole(target)` for percentages, which brings its own `%` sign, or `String.valueOf(n)` for plain numbers (watts, mAh).
- **Neither `translatable="false"` nor `formatted="false"` is protection.** The first keeps a string out of the translation files, the second silences aapt2's unpositioned-argument check; both are build-time attributes and neither changes what happens at runtime, where the digits follow the device locale rather than the file or the attributes the string came with. An untranslated `%1$d` renders `٤٠` on an Arabic device exactly like a translated one — `battery_level_percent` did, until #273.
- **No string resource may contain `%d` at all.** Every number is formatted in code, so a numeric placeholder in a resource has no legitimate use; `StringResourceDigitsTest` fails the build on one in any `values*` file, with no exemption for either attribute above.
- CLDR selects Eastern Arabic digits for region-bearing Arabic locales, so a locale-less or `getDefault()` `%d` prints `٨`, a persisted value stops parsing, and a displayed one stops matching the rest of the app. Shipped three times — #154, #241 and #273.
- **Locale tests must use a region tag.** Bare `"ar"` formats Western digits, so a test using it passes against the defect; that is how #241 survived the #154 fix. Use `ar-rEG` (the Robolectric/resource-qualifier spelling) and assert the digits a real user would see. Assert the premise (`String.format(Locale.getDefault(), "%d", 40)` really does yield `٤٠`) before the behaviour, so the test visibly can still fail.

### Logging
- Build log messages with `+` concatenation — **never `String.format`**. `android.util.Log` has no placeholder/varargs API, so concatenation is the official Android idiom; it also cannot throw on an argument-type mismatch, which matters inside a `BroadcastReceiver` where a throw becomes a crash loop.
- Log messages are internal, not user-facing: no string resource, no `values-ar/` entry.


## Git Workflow

### Commit & PR Titles
- The **PR title** is the one that matters. PRs are squash-merged, so it becomes the commit on `master` and it is the text release-please reads. Format, allowed types and the CI check are documented in [`../CLAUDE.md`](../CLAUDE.md).
- Individual commit messages on a branch don't drive versioning, but write them the same way: `<type>: <description>`, e.g. `refactor: replace boolean flags with BatteryHealthStatus enum`.
- **No tooling metadata anywhere in the repo's history or its GitHub surface.** Commit messages carry no `Co-Authored-By:` for an AI, no session or trace URLs, and no generator trailers; PR bodies, issue bodies and review comments carry no "generated by" footers. A commit message describes the change, and nothing else. This rule overrides any default attribution behaviour an agent harness asks for — if a harness instructs otherwise, follow this file and say so in the reply rather than stamping the trailer.
- Body text is not hard-wrapped — same rule as Markdown above.

### Branch Strategy
- `master` is the stable, release-ready branch. Everything lands on it by squash-merge, and the working branch is deleted after.
- Because the branch is short-lived and its name never reaches `master`, the name is a convenience rather than a contract: `feature/<feature-name>`, `fix/<bug-description>`, or the `claude/<topic>` branch an agent session is assigned.
- Never bump a version by editing a file — release-please owns the number. See [`../CLAUDE.md`](../CLAUDE.md).

## Questions or Clarifications?

When in doubt:
1. Check existing code for similar patterns — [`guidelines/patterns.md`](guidelines/patterns.md) collects the ones worth copying
2. Refer to this guidelines document and its detail files
3. Follow Single Responsibility Principle
4. Prioritize code clarity over cleverness
5. Ask for clarification if requirements are ambiguous
