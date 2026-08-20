# BattWatch — testing

> Detail companion to [`../guidelines.md`](../guidelines.md). Read this before adding or changing tests.

## Testing Strategy

The project uses **JUnit 4** for pure logic, with **Robolectric** and **Mockito** available for framework-dependent tests. `build.gradle` sets `testOptions.unitTests.includeAndroidResources = true` so Robolectric can resolve app resources.

### Unit Tests (JUnit) — prefer these
- Extract pure, Android-free helpers and test them directly (e.g. `SystemService.estimateFullCapacityMah`, `NotificationService.isWithinTimeRange`, `TemperatureUtils`, `BatteryDO.getBatteryPercentage`).
- Cover edge cases: division by zero, boundaries, unsupported/`MIN_VALUE` readings.

### Framework Tests (Robolectric + Mockito)
- Use `@RunWith(RobolectricTestRunner.class) @Config(sdk = 34)` — targetSdk 36 is beyond Robolectric's supported range.
- SharedPreferences-backed state (cycle tracker, design capacity) via a real `ApplicationProvider` context.
- Receivers: drive `onReceive` with a sticky `ACTION_BATTERY_CHANGED` intent and `mockStatic(NotificationService.class)` to assert which alert is chosen; reset static de-dup state between tests.

### Add tests with new logic
- New business logic (thresholds, state machines, parsing) should ship with tests that would fail on regression.

