# Code Review Guidelines

These are the coding standards and best practices for the BattWatch Android project (formerly Simple Battery Notifier).

> **Audience:** this is the human-facing standard for contributors and reviewers. The AI code-review agent has a machine-facing companion at [`.claude/guidelines.md`](.claude/guidelines.md); keep the two in sync when a standard changes.

---

## 📋 Table of Contents

- [Build Configuration](#build-configuration)
- [Code Style](#code-style)
- [Null Safety](#null-safety)
- [Exception Handling](#exception-handling)
- [Variables](#variables)
- [Code Organization](#code-organization)
- [Android Best Practices](#android-best-practices)
- [Localization](#localization)
- [Performance](#performance)
- [Comments & Documentation](#comments--documentation)

---

## 🔧 Build Configuration

### SDK Versions

- **minSdk**: 26 (Android 8.0 Oreo) - Can be bumped if required
- **targetSdk**: 36 (Latest)
- **Gradle**: 9.2.1
- **JDK**: 25

### Dependencies

- Use AndroidX libraries (not legacy support libraries)
- Keep dependencies up to date
- Document why each dependency is needed

---

## 🎨 Code Style

### 1. Curly Brackets

**Always use curly brackets, even for single-line statements.**

```java
// ❌ BAD
if(condition) doSomething();

if(condition)
    doSomething();

// ✅ GOOD
if (condition) {
    doSomething();
}
```

**Why?** Prevents bugs when adding additional lines later.

### 2. Null Checks

**Use `isNull()` and `nonNull()` instead of `== null` and `!= null`.**

```java
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

// ❌ BAD
if(object == null) {}
if(object != null) {}

// ✅ GOOD
if(isNull(object)) {}
if(nonNull(object)) {}
```

**Why?** More readable and follows modern Java conventions.

### 3. Avoid Condition Inversions

**Name methods to match how they're used - avoid always inverting boolean methods.**

```java
// ❌ BAD - Method always called inverted
private boolean hasPermission() {
	return checkPermission();
}

if(!hasPermission()) {  // Always inverted!
	return;
}

// ✅ GOOD - Method name matches usage
private boolean lacksPermission() {
	return !checkPermission();
}

if(lacksPermission()) {  // No inversion needed
	return;
}
```

**Why?** Reduces cognitive load and makes code more readable. If you're always inverting a method, the name is wrong.

**Guideline:** If your IDE warns "Calls to method X are always inverted", rename the method.

### 4. Line Width & Wrapping

- Maximum line width: **160 characters** — this applies to comments and JavaDoc exactly as it does to code. Don't wrap a comment at 100 because the paragraph around it happens to be narrow; use the full width.
- Don't over-wrap everything — **if it fits on one line, put it on one line**
- Break long lines at logical points (method calls, operators)
- **Markdown is never hard-wrapped.** In `.md` files — and in commit messages, PR bodies and issue bodies — let each paragraph run as one long line and let the editor soft-wrap it. Hard line breaks inside a paragraph make every later edit re-flow the whole block and turn a one-word change into a multi-line diff.

```java
// ✅ GOOD - Reasonable line length
final String value = prefs.getString(context.getString(R.string.key), defaultValue);

// ❌ BAD - wrapped for no reason; this is 121 characters and fits
private static LevelAlertDecision decideDischarging(LevelAlertState state, int percentage,
                                                    LevelAlertConfig config) {

// ✅ GOOD - one line, under 160
private static LevelAlertDecision decideDischarging(LevelAlertState state, int percentage, LevelAlertConfig config) {
```

### 5. More Than 4 Parameters — One Per Line

**A signature, constructor or call with more than 4 parameters gets one per line — even when it would fit inside 160 characters.** Four or fewer stay on a single line whenever they fit.

Past four, a run-on list stops being scannable: you can't tell at a glance how many arguments there are, and a wrong-order argument hides in the middle of it. The line-width rule is about *not* wrapping needlessly; this rule is about a count the eye can't parse, so it wins over "but it fits".

```java
// ❌ BAD - 6 components, packed onto two arbitrary lines
record LevelAlertConfig(int criticalLevel, int warningLevel, int chargeTarget, boolean warningEnabled,
                        boolean fullNotifyEnabled, boolean alertEveryTick) {

// ✅ GOOD - more than 4, so one per line
record LevelAlertConfig(
		int criticalLevel,
		int warningLevel,
		int chargeTarget,
		boolean warningEnabled,
		boolean fullNotifyEnabled,
		boolean alertEveryTick) {

// ✅ GOOD - exactly 4 and it fits, so one line
static boolean nextFullSeen(TemperatureStats previous, int levelPercent, boolean chargeComplete, int chargeTarget) {
```

### 6. Chained Calls — All On One Line, Or One Per Line

**Builder chains and streams go on a single line if they fit. If they don't, every call in the chain gets its own line** — never a half-and-half split where some calls share a line and others don't.

```java
// ✅ GOOD - fits, so one line
return prefs.edit().putInt(PREF_LEVEL, level).apply();

// ❌ BAD - arbitrary grouping; where the breaks fall tells the reader nothing
return prefs.edit().putInt(PREF_PREV_LEVEL, state.prevLevel())
            .putInt(PREF_PREV_TYPE, id).putBoolean(PREF_FULL_NOTIFIED, state.fullNotified());

// ✅ GOOD - doesn't fit, so one call per line
return prefs.edit()
            .putInt(PREF_PREV_LEVEL, state.prevLevel())
            .putInt(PREF_PREV_TYPE, AlertType.persistedId(state.prevType()))
            .putBoolean(PREF_FULL_NOTIFIED, state.fullNotified())
            .putBoolean(PREF_FULL_ALERT_SHOWN, state.fullAlertShown());
```

### 7. No FQNs (Fully Qualified Names)

**Never use fully qualified names in code - use imports instead.**

```java
// ❌ BAD

android.content.Intent intent=new android.content.Intent();

// ✅ GOOD
import android.content.Intent;

Intent intent = new Intent();
```

---

## 🛡️ Null Safety

### Always Check System Services

```java
// ❌ BAD - Can crash if service is null
NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
manager.notify(id, notification); // NPE possible!

// ✅ GOOD
final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
if(nonNull(manager)) {
	manager.notify(id, notification);
}
```

### Check Resources That Can Fail

```java
// ❌ BAD
Bitmap bitmap = BitmapFactory.decodeResource(resources, R.drawable.image);
imageView.setImageBitmap(bitmap); // NPE if decode fails!

// ✅ GOOD
final Bitmap bitmap = BitmapFactory.decodeResource(resources, R.drawable.image);
if(nonNull(bitmap)) {
	imageView.setImageBitmap(bitmap);
}
```

---

## 🧯 Exception Handling

### 1. Never Swallow Exceptions Silently

**A `catch` block must either take real recovery action or log — never both empty and silent.**

```java
// ❌ BAD - swallowed, no log, and overly broad
try {
	value = Integer.parseInt(input);
} catch (Exception e) {
	value = -1;
}

// ✅ GOOD - if a catch is unavoidable, catch the narrowest type and log it
try {
	return Settings.Global.getInt(resolver, ZEN_MODE) == ZEN_MODE_IMPORTANT_INTERRUPTIONS;
} catch (Settings.SettingNotFoundException e) {
	return false; // setting genuinely absent on this device — expected default
}
```

**Why?** An empty/sentinel catch with no log hides real bugs and conflates unexpected failures with expected ones.

### 2. Don't Use Exceptions for Expected Validation

**If something is "normal to fail" (user/persisted input), handle it with an ordinary branch — not `try/catch`.**

`NumberFormatException` and friends are *unchecked*, so you can validate first and skip the catch entirely:

```java
// ❌ BAD - exception as the validation path
try {
	value = Integer.parseInt(trimmed);
} catch (NumberFormatException e) {
	value = -1; // "invalid" signalled via exception
}

// ✅ GOOD - validate, then parse (provably can't throw)
if (!trimmed.matches("\\d{1,5}")) {
	showRangeError();
	return false;
}
final int value = Integer.parseInt(trimmed); // safe: matched \d{1,5}
```

### 3. Catch the Narrowest Type

- Prefer `ActivityNotFoundException` over `Exception` around `startActivity`.
- A catch that gives the user feedback (e.g. a Toast) is fine and is **not** "silent".
- Accepted idioms (document why): `unregisterReceiver` throwing `IllegalArgumentException` when already unregistered; `Settings.SettingNotFoundException` → default.

---

## 📦 Variables

### 1. Use `final` for Variables Not Reassigned

```java
// ❌ BAD
String name = "John";
int age = 25;

// ✅ GOOD
final String name = "John";
final int age = 25;
```

**Why?** Improves readability and prevents accidental reassignment.

> **Parameters are the exception — do not mark method/constructor parameters `final`** in new or edited code. Effectively-final already covers lambda capture, so the keyword only adds noise. Legacy code still carries `final` params (an older convention) and migrates as methods are otherwise touched — a full sweep isn't required.
>
> ```java
> // ❌ BAD - final on parameters
> public int criticalLevel(final Context context) { ... }
>
> // ✅ GOOD - final on locals, not parameters
> public int criticalLevel(Context context) {
> 	final SharedPreferences prefs = getPrefs(context);
> 	// ...
> }
> ```

### 2. Declare Variables Close to Usage

```java
// ❌ BAD
final String name = getName();
final int age = getAge();
// ... 50 lines of code ...
System.out.println(name);

// ✅ GOOD
// ... 50 lines of code ...
final String name = getName();
System.out.println(name);
```

---

## 🏗️ Code Organization

### 1. Early Returns - Avoid Nested Blocks

**Return early to reduce nesting depth.**

```java
// ❌ BAD - Deep nesting
public void process(String input) {
	if (input != null) {
		if (input.length() > 0) {
			if (isValid(input)) {
				doSomething(input);
			}
		}
	}
}

// ✅ GOOD - Early returns
public void process(String input) {
	if (isNull(input)) {
		return;
	}
	if (input.isEmpty()) {
		return;
	}
	if (!isValid(input)) {
		return;
	}
	doSomething(input);
}
```

### 2. SOLID Principles

#### Single Responsibility Principle (SRP)

Each method/class should do ONE thing.

```java
// ❌ BAD - Does too much
public void processUserDataAndSendEmail(User user) {
	validateUser(user);
	saveToDatabase(user);
	sendWelcomeEmail(user);
	logActivity(user);
}

// ✅ GOOD - Separate concerns
public void processUser(User user) {
	validateUser(user);
	saveToDatabase(user);
}

public void sendWelcomeEmail(User user) {
	// Email logic only
}
```

#### DRY Principle (Don't Repeat Yourself)

Extract repeated code into reusable methods.

```java
// ❌ BAD - Repeated code
final NotificationManager manager1 = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
manager1.notify(id1, notification1);

final NotificationManager manager2 = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
manager2.notify(id2, notification2);

// ✅ GOOD - Extracted to method
private void sendNotification(Context context, int id, Notification notification) {
	final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	if (nonNull(manager)) {
		manager.notify(id, notification);
	}
}
```

### 3. Method Size

- Maximum method length: **~50 lines**
- If longer, break into smaller methods
- Each method should have a clear, single purpose

### 4. Remove Unused Code

```java
// ❌ BAD
private static final String UNUSED_CONSTANT = "value";
private int unusedVariable;

public void unusedMethod() {
	// Never called
}

// ✅ GOOD
// Delete all unused code - don't comment it out
```

---

## 📱 Android Best Practices

### 1. Always Check Permissions

```java
// ❌ BAD - No permission check
notificationManager.notify(id, notification);

// ✅ GOOD - Check permission first (Android 13+)
private boolean hasNotificationPermission(Context context) {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
				== PackageManager.PERMISSION_GRANTED;
	}
	return true;
}

if(hasNotificationPermission(context)) {
	notificationManager.notify(id, notification);
}
```

### 2. Use Modern APIs

```java
// ❌ BAD - Deprecated
new Handler().post(runnable);
startActivityForResult(intent, requestCode);

// ✅ GOOD - Modern
new Handler(Looper.getMainLooper()).post(runnable);
activityResultLauncher.launch(intent);
```

### 3. Thread Safety for Static Fields

```java
// ❌ BAD - Not thread-safe
private static boolean isActive;

// ✅ GOOD - Thread-safe
private static volatile boolean isActive;
```

### 4. Use ExecutorService Instead of Raw Threads

```java
// ❌ BAD
new Thread(() ->{
	doBackgroundWork();
}).start();

// ✅ GOOD
private static final ExecutorService executor = Executors.newSingleThreadExecutor();
executor.execute(() -> doBackgroundWork());
```

---

## 🌍 Localization

**The app ships an Arabic translation (`res/values-ar/`), so every user-facing string must be localizable.**

### 1. No Hardcoded User-Facing Strings

```java
// ❌ BAD - hardcoded English, can never be translated
healthStatusText.setText("Excellent");
Toast.makeText(this, "Health data reset", Toast.LENGTH_SHORT).show();

// ✅ GOOD - string resource
healthStatusText.setText(R.string.health_grade_excellent);
```

Keep the mapping from a value/enum to its string resource in a UI/service layer (mirroring `SystemService.getHealthString`), not as inline literals.

### 2. Keep `values-ar/` in Parity

- Every new user-facing string in `values/strings.xml` needs a matching entry in `values-ar/strings.xml`.
- Quick check: diff the `<string name=…>` lists of the two files.
- Consider enabling the `MissingTranslation` lint check as a build gate.

### 3. Don't Translate Internal Keys

Identifiers are **not** copy — leave them out of `values-ar/`:

- `_pref_key_*`, `_pref_value_*`, `pref_category_*`, `extra_category`, URIs, and URLs.

### 4. Format Strings & RTL

- Use positional format args for dynamic content: `<string name="notification_status_content">%1$d%% · %2$s · %3$s</string>`.
- Use `start`/`end` (not `left`/`right`) in layouts and test with an RTL language.

### 5. Numbers Are Always Western Digits

**Every number a user sees renders in Western digits (`85`), never Eastern Arabic (`٨٥`) — in any locale.** The words around a number are translated; the number itself is not. This is a product decision, and `BatteryPercentFormatter` has enforced it since #96.

That means `Locale.ROOT` for **all** numeric formatting — persisted *and* user-facing alike. `Locale.getDefault()` has no numeric use in this app.

```java
// ❌ BAD - no locale: under a region-bearing Arabic locale this yields "٠٨:٣٠"
String.format("%02d:%02d", hour, minute);

// ❌ BAD - getDefault() localizes the digits: "٤٠%" on an ar-EG device
String.format(Locale.getDefault(), levelDescriptionFormat, level);

// ✅ GOOD - Western digits regardless of language
String.format(Locale.ROOT, "%02d:%02d", hour, minute);

// ✅ GOOD - percentages go through the shared formatter
BatteryPercentFormatter.formatWhole(level); // "85%"
```

**`getString(id, someInt)` is the trap.** `Resources.getString` formats with the **configuration** locale — the device's, not the one the string was declared in — so a `%1$d` placeholder produces Eastern digits on `ar-EG`/`ar-SA`/`ar-JO` even though no `String.format` appears in your code. Use a `%1$s` placeholder and pass an already-formatted string:

```xml
<!-- ❌ BAD - %1$d is formatted by the resource locale -->
<string name="notification_almost_full_content">Reached your %1$d%% charge target</string>

<!-- ✅ GOOD - %1$s takes the number exactly as we formatted it -->
<string name="notification_almost_full_content">Reached your %1$s charge target</string>
```

```java
// ✅ GOOD - the formatter owns the digits and the '%' sign
context.getString(R.string.notification_almost_full_content, BatteryPercentFormatter.formatWhole(target));
```

**`translatable="false"` is not protection.** It keeps a string out of the translation files; it does nothing about formatting, because the digits are chosen by the device's locale rather than by the file the string came from. An untranslated `%1$d` renders `٤٠` on an Arabic device exactly like a translated one — `battery_level_percent` did, until #273.

**No string resource may contain `%d` at all.** Since every number is formatted in code, a numeric placeholder in a resource has no legitimate use, and `StringResourceDigitsTest` fails the build on one in any `values*` file. That is a static property of the catalogue, so it is checked as one rather than string by string.

**Locale tests must use a region tag.** Bare `"ar"` formats Western digits, so a test written against it passes even when the defect is present — that is exactly how #241 survived the #154 fix. Use `ar-rEG` (the Robolectric/resource-qualifier spelling of `ar-EG`) and assert the digits a real user would see. Assert the premise (`String.format(Locale.getDefault(), "%d", 40)` really does yield `٤٠`) first, so the test visibly can still fail.

**Why?** CLDR selects the Eastern Arabic numbering system for region-bearing Arabic locales (`ar-SA`, `ar-EG`, `ar-JO`), so `%d` prints `٨` rather than `8`, a persisted value stops parsing, and a displayed one stops matching the rest of the app. This shipped twice — issues #154 and #241.

### 6. Don't Use `String.format` in Logs

**Build log messages with `+` concatenation.**

```java
// ❌ BAD - runtime crash if the format and the argument types ever disagree
Log.i(TAG, String.format("Charger connected (Battery: %d%%)", percentage));

// ✅ GOOD - cannot fail, and no locale to get wrong
Log.i(TAG, "Charger connected (Battery: " + percentage + "%)");
```

**Why?** `android.util.Log` has no placeholder/varargs API — `Log.i(tag, msg)` and `Log.i(tag, msg, throwable)` are the whole surface, so concatenation is the official Android idiom. It also cannot throw on an argument-type mismatch the way `String.format` can, which matters inside a `BroadcastReceiver` where a throw becomes a crash loop. Concatenating a number always produces Western digits, so no locale is involved.

Log messages are not user-facing, so they are **not** translated and need no string resource.

---

## ⚡ Performance

### 1. Cache Expensive Operations

```java
// ❌ BAD - Decode every time
public void showNotification() {
	Bitmap icon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher);
	notification.setLargeIcon(icon);
}

// ✅ GOOD - Cache the bitmap
private static Bitmap cachedIcon;

private Bitmap getIcon() {
	if (isNull(cachedIcon)) {
		cachedIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher);
	}
	return cachedIcon;
}
```

### 2. Extract Repeated Method Calls

**Call methods once and store the result - don't call repeatedly in the same scope.**

```java
// ❌ BAD - Calls getResources() 8 times!
if(usbCharge) {
	charger = context.getResources().getString(R.string.charger_connected_usb);
	chargerSource = context.getResources().getString(R.string.charger_usb);
} else if(acCharge) {
	charger = context.getResources().getString(R.string.charger_connected_ac);
	chargerSource =context.getResources().getString(R.string.charger_ac);
} else if(wirelessCharge){
	charger = context.getResources().getString(R.string.charger_connected_wireless);
	chargerSource = context.getResources().getString(R.string.charger_wireless);
}

// ✅ GOOD - Call once, reuse the reference
final Resources resources = context.getResources();
if(usbCharge) {
	charger = resources.getString(R.string.charger_connected_usb);
	chargerSource = resources.getString(R.string.charger_usb);
} else if(acCharge) {
	charger = resources.getString(R.string.charger_connected_ac);
	chargerSource = resources.getString(R.string.charger_ac);
}else if (wirelessCharge) {
	charger = resources.getString(R.string.charger_connected_wireless);
	chargerSource = resources.getString(R.string.charger_wireless);
}
```

**Why?** Reduces method call overhead and improves readability.

**Common cases:**

- `context.getResources()` - Extract to `final Resources resources`
- `getActivity()` - Extract to `final Activity activity`
- `view.getContext()` - Extract to `final Context context`
- Repeated property access in loops

### 3. Avoid Repeated System Service Calls

```java
// ❌ BAD
public void method1() {
	NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	mgr.notify(1, n1);
}

public void method2() {
	NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	mgr.notify(2, n2);
}

// ✅ GOOD - Extract to helper
private NotificationManager getNotificationManager(Context context) {
	return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
}
```

### 4. Report Performance Issues

When reviewing, flag these:

- ❌ Multiple bitmap decodes
- ❌ Repeated database queries in loops
- ❌ Synchronous I/O on main thread
- ❌ Inefficient algorithms (O(n²) when O(n) exists)
- ❌ Memory leaks (static context references)

---

## 📝 Comments & Documentation

### 1. Remove Unnecessary Comments

```java
// ❌ BAD - Obvious comments
// Get the name
final String name = getName();

// Loop through users
for(User user :users) {

// ❌ BAD - Outdated
// Created by John Doe on 01/01/2015

// ✅ GOOD - No comment needed for obvious code
final String name = getName();
```

### 2. Add Necessary Comments

```java
// ✅ GOOD - Explain WHY, not WHAT
// Handle overnight time ranges (e.g., 8:00 PM to 6:00 AM)
if(endHour <=startHour) {
	endCal.add(Calendar.DATE, 1);
}

// ✅ GOOD - Document complex business logic

/**
 * Check if the device is in Do Not Disturb mode.
 * Returns false if the ZEN_MODE setting is not available (pre-API 23).
 */
private boolean isInDoNotDisturbMode() {
	try {
		return ZEN_MODE_IMPORTANT_INTERRUPTIONS == Settings.Global.getInt(resolver, ZEN_MODE);
	} catch (Settings.SettingNotFoundException e) {
		return false;
	}
}
```

### 3. JavaDoc for Public APIs

```java
/**
 * Send a battery status notification
 *
 * @param context The application context
 * @param type    Notification type (CRITICAL_TYPE, WARNING_TYPE, or FULL_LEVEL_TYPE)
 */
public static void sendNotification(Context context, int type) {
	// Implementation
}
```

---

## ✅ Spelling & Naming

### Fix Spelling Errors

```java
// ❌ BAD
boolean withInTime; // Should be "within"
String recieve;     // Should be "receive"

// ✅ GOOD
boolean withinTime;
String receive;
```

### Use Clear Names

```java
// ❌ BAD
int tmp;
String s;
List<String> list1;

// ✅ GOOD
int batteryLevel;
String userName;
List<String> notificationChannels;
```

---

## 📊 Review Checklist

When reviewing code, check:

- [ ] Uses `isNull()`/`nonNull()` instead of `== null`/`!= null`
- [ ] All curly brackets present (even single-line)
- [ ] Locals marked `final` where appropriate; parameters **not** marked `final`
- [ ] No FQNs (uses imports)
- [ ] Line width ≤ 160 characters — comments and JavaDoc included — and nothing wrapped that would fit on one line
- [ ] More than 4 parameters → one per line; chained calls all on one line or one per line
- [ ] Markdown (and commit/PR/issue text) not hard-wrapped; no tooling metadata or generator footers
- [ ] Early returns instead of deep nesting
- [ ] No code duplication (DRY)
- [ ] Methods ≤ 50 lines
- [ ] All null checks in place
- [ ] No silent/broad `catch`; expected failures validated, not caught
- [ ] No hardcoded user-facing strings; `values-ar/` kept in parity
- [ ] Every number a user sees is Western digits — `Locale.ROOT` everywhere, `%1$s` not `%1$d` in resources, `translatable="false"` included
- [ ] Locale tests use a region tag (`ar-rEG`), never bare `ar`
- [ ] Log messages use `+` concatenation, not `String.format`
- [ ] Permissions checked (Android 13+)
- [ ] Modern APIs used (no deprecated)
- [ ] Thread-safe for static fields
- [ ] No performance issues
- [ ] Comments are helpful, not obvious
- [ ] Spelling is correct
- [ ] No unused code/imports
- [ ] Follows SOLID principles

---

## 🎯 Summary

**Remember:**

1. **Safety first** - Null checks, permissions, thread safety
2. **Performance matters** - Cache expensive operations
3. **Readability counts** - Clear names, early returns, proper formatting
4. **Quality over quantity** - Small focused methods beat large complex ones
5. **Modern practices** - Use latest Android APIs and patterns

**When in doubt:**

- If it looks complex, simplify it
- If it's repeated, extract it
- If it's unclear, document it
- If it's unused, delete it

---

*Last updated: 2026-07-25*
