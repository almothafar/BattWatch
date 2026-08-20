# BattWatch — Android platform rules

> Detail companion to [`../guidelines.md`](../guidelines.md). Read this before touching Android framework code: system services, API-level branches, deprecated APIs, UI/layout, threading, permissions, or the Gradle/ProGuard setup.

## Android Specifics

### API Level Support
- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 36
- **Compile SDK**: API 36
- Use modern APIs with fallbacks for older versions when needed

### API Version Handling
```java
// Modern API (API 31+) with fallback
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    // Use VibratorManager
    final VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
    vibrator = vibratorManager.getDefaultVibrator();
} else {
    // Use deprecated Vibrator for API 26-30 (with suppression)
    @SuppressWarnings("deprecation")
    final Vibrator systemService = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    vibrator = systemService;
}
```

### Deprecation Handling
- **Never ignore deprecation warnings** - address them properly:
  1. Replace with modern API if available
  2. Add `@SuppressWarnings("deprecation")` only if deprecated API is required for minSdk support
  3. Add comment explaining why deprecated API is necessary
- **Document workarounds**: If using deprecated API, explain the necessity in code comments

### Reflection Usage
- **Avoid reflection.** Prefer public APIs; reflection into internal/private Android APIs is blocked by non-SDK restrictions on modern Android and should not be used.
  - Historical note: battery capacity was once read by reflecting into the internal `PowerProfile`. That was **removed** — `SystemService.getBatteryCapacity` now estimates full capacity from public `BatteryManager` properties (`BATTERY_PROPERTY_CHARGE_COUNTER ÷ BATTERY_PROPERTY_CAPACITY`), returning 0 when unsupported.
- If reflection is ever truly unavoidable, document it thoroughly (why it's needed, what can fail, how failure degrades gracefully) and keep the pure/computational part unit-testable.

### UI Components
- **Edge-to-Edge Display**: Enabled via `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`
- **System Bar Colors**: Set in themes (values/themes.xml), not programmatically (deprecated in API 35)
- **Activity Results**: Use `ActivityResultLauncher` instead of deprecated `startActivityForResult()`
- **Fragments**: Use AndroidX fragments, never use deprecated `setTargetFragment()`

### Threading
- Use `Handler(Looper.getMainLooper())` constructor - `Handler()` is deprecated
- Post UI updates to main thread via Handler
- Use Timer/TimerTask for periodic updates (as currently implemented)

## Accessibility

### UI Guidelines
- Provide content descriptions for ImageViews and IconButtons
- Ensure minimum touch target size (48dp)
- Support dynamic text sizing
- Test with TalkBack enabled

### RTL Support
- Use start/end instead of left/right for layouts
- Test with RTL languages (e.g., Arabic)
- Current workaround in BatteryDetailsFragment should be replaced with proper RTL layout

## Security

### Permissions
- Request only necessary permissions
- Document why each permission is needed
- Handle permission denial gracefully

### Data Storage
- Store preferences using SharedPreferences
- Never store sensitive data in plain text
- Use appropriate scoped storage for files

## Performance Considerations
- Minimize object allocations in frequently called methods (e.g., `onDraw`)
- Reuse Paint objects instead of creating new ones
- Use appropriate data structures (e.g., HashMap for key-value lookups)
- Avoid unnecessary boxing/unboxing

## Build Configuration

### Gradle
- Keep dependencies up to date
- Use version catalogs for dependency management (if migrating to modern Gradle)
- Run `./gradlew clean build` before committing major changes

### ProGuard/R8
- Keep rules for reflection usage
- Test release builds thoroughly
- Document any custom ProGuard rules

