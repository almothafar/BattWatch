# Simple Battery Notifier

An Android app that monitors the device battery and raises notifications for low/critical/full
levels, high temperature, and battery health. This glossary pins down the terms the app's UI and
code use so they don't drift.

## Language

**Drain rate**:
How fast the battery level is falling while discharging, expressed in percentage-points per hour
(%/h). The normalized, device-size-independent headline metric for consumption.
_Avoid_: consumption, usage, speed.

**Charge rate**:
How fast the battery level is rising while charging, in %/h. The charging counterpart of the drain
rate.
_Avoid_: charging speed.

**Instantaneous current**:
The live current flowing in or out of the battery right now, in milliamps (mA), read from
`BatteryManager.BATTERY_PROPERTY_CURRENT_NOW`. Shown as raw detail; not normalized, so it is not
colored on its own.
_Avoid_: amperage, draw, consumption.

**Design capacity**:
The battery's rated full capacity when new (mAh), from the manufacturer's spec. User-entered or
best-effort auto-detected from the kernel. Distinct from the measured current full capacity.
_Avoid_: rated capacity (in prose only), max capacity.

**Charge target**:
The level the user wants charging to stop at, in percent. The full-battery alert fires when the
battery reaches it while charging (default 90%, range 80–100), and it is also what "this charge is
done" means for the temperature range. At 100% it means a genuinely complete charge, and the alert
waits for one.
_Avoid_: charge limit, charge cap (that is the OEM hardware feature), threshold.

**Temperature range**:
The lowest and highest battery temperature seen since the battery last finished charging, in the
user's display unit. Accumulated from the readings the monitoring service observes; completing a
charge is the only thing that starts a new range.
_Avoid_: temperature history, heat range, thermal range.

**Stable capacity**:
The learned running average of the measured full capacity (mAh), built from spaced, trusted
per-tick estimates. Slow-moving by design: it is the denominator of the sub-percent battery
display, so the charge counter's live movement shows up in the decimals instead of cancelling out.
Distinct from design capacity and from the single-tick estimate.
_Avoid_: averaged capacity, smoothed capacity.
