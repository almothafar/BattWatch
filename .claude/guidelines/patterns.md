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
- **Decision**: Keep the papercut artwork baked into `ic_launcher_foreground` rather than splitting it into genuine foreground/background layers, and make `ic_launcher_background` a measured vertical gradient instead of a flat colour (#289)
- **Rationale**:
  - A real background layer has to fill all 108dp, and only a 71.5dp opaque square of artwork exists, with no vector source in the repo — a split means inventing 18.25dp of wave on every side, at five densities, out of raster, plus inpainting where the shield's drop shadow sits
  - The icon is the app's identity on Play: a bad redraw is visible to every user permanently, while the artefact it fixes is a transient parallax band on the launchers that animate the two layers
  - The background is only visible where the foreground is not, which under a static mask is nowhere — so changing it cannot regress the common case, which is what makes the gradient worth doing when the redraw is not
- **Implementation**: `res/drawable/ic_launcher_background.xml` with stops sampled from `mipmap-xxxhdpi/ic_launcher_foreground.png` — `#D8F3EC` → `#A3E3D7` → `#6DD3C5`, averaged over the outer eighth of each side so the centred shield does not skew them. The `@color` resource of the same name is gone; the drawable is now the single owner of that name.
