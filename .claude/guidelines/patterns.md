# BattWatch — patterns, JavaDoc & decision log

> Detail companion to [`../guidelines.md`](../guidelines.md). The house patterns to copy, the JavaDoc/comment format, and why the current shapes were chosen. Check here before inventing a new shape for something the codebase already does.

## Architecture Decisions

### Design Patterns
- **Builder Pattern**: Use method chaining with `return this` for data objects (e.g., BatteryDO)
- **Enum Pattern**: Prefer enums over boolean flags for state representation
  - Example: Use `BatteryHealthStatus.WARNING` instead of `isWarning` boolean
- **Single Responsibility Principle**: One method, one clear purpose
  - Method names should clearly indicate what they do
  - Avoid side effects - methods shouldn't mutate objects unexpectedly
  - Split methods that do multiple things into separate methods

### Data Management
- **Data Objects**: Use simple POJOs with getters/setters
- **State Representation**: Use enums for mutually exclusive states
- **Immutability**: Make internal data classes immutable where possible (e.g., `BatteryExtras`)

## Documentation Standards

### JavaDoc Requirements
- **All public methods** must have JavaDoc
- **All classes** must have a brief description
- **Complex private methods** should have JavaDoc explaining purpose

### JavaDoc Structure
```java
/**
 * Brief description of what the method does
 * <p>
 * Optional: Extended description with implementation details,
 * warnings, or important notes.
 *
 * @param paramName Description of parameter
 * @return Description of return value
 * @throws ExceptionType When and why this exception is thrown (if applicable)
 */
```

### Comment Guidelines
- **Why, not what**: Comments should explain reasoning, not repeat the code
- **TODOs**: Mark workarounds that should be addressed later
  - Example: `// TODO: This is a workaround - should be handled with proper RTL layout support`
- **Critical sections**: Mark important null checks or edge cases
  - Example: `// CRITICAL: Check for null batteryDO`
- **API Level notes**: Document API compatibility decisions
  - Example: `// BATTERY_PLUGGED_WIRELESS added in API 17`

## Common Patterns in This Codebase

### Null Safety Pattern
```java
final BatteryDO batteryDO = SystemService.getBatteryInfo(context);
if (isNull(batteryDO)) {
    Log.w(TAG, "Unable to retrieve battery information");
    return; // or provide fallback
}
// Use batteryDO safely
```

### Builder Pattern (BatteryDO)
```java
batteryDO.setLevel(level)
         .setScale(scale)
         .setStatus(status)
         .setPowerSource(chargerType);
```

### Switch Expressions
```java
return switch (health) {
    case BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealthStatus.GOOD;
    case BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealthStatus.CRITICAL;
    default -> BatteryHealthStatus.UNKNOWN;
};
```

### Resource Cleanup
```java
try (final TypedArray styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.CircularProgressBar)) {
    // Use styledAttributes
    // No need to call recycle() - try-with-resources handles it
}
```

## Recent Architectural Decisions

### BatteryHealthStatus Enum (2025)
- **Decision**: Replace boolean flags (`warningHealth`, `criticalHealth`) with `BatteryHealthStatus` enum
- **Rationale**:
  - Type safety
  - Single source of truth
  - No hidden side effects
  - Easier to extend with new health states
- **Implementation**: `BatteryHealthStatus` with values: GOOD, WARNING, CRITICAL, UNKNOWN

### Method Separation (2025)
- **Decision**: Split `determineHealthString` into two separate methods
- **Rationale**: Single Responsibility Principle - avoid side effects
- **Implementation**:
  - `determineHealthStatus(int health)` - returns enum, no side effects
  - `getHealthString(int health, Resources resources)` - returns string, no side effects


### Adaptive-Icon Background Gradient (2026)
- **Decision**: Keep the artwork baked into `ic_launcher_foreground`; make `ic_launcher_background` a gradient measured from it rather than a flat colour (#289)
- **Rationale**: A real background layer must fill 108dp and only 71.5dp of art exists, with no vector source — a split means redrawing the icon. The background is invisible under static masks, so a gradient improves parallax without risking the common case.
- **Implementation**: `res/drawable/ic_launcher_background.xml`, stops `#D8F3EC` → `#A3E3D7` → `#6DD3C5` sampled from the xxxhdpi foreground; the `@color` of the same name is gone.
