# BattWatch

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

**Screen-on drain** / **Screen-off drain**:
The drain since the battery was last unplugged, split by whether the screen was on or off at the time, each shown as an average current, a %/h to one decimal, and how long the battery would last in that state. Unlike the drain rate, which covers the last ten minutes, it accumulates over the whole time off the charger, so it tells usage apart from idle drain. A lit lockscreen counts as on and an always-on display as off; plugging in starts both over.
_Avoid_: active/idle drain, foreground/background drain.

**Time remaining**:
The estimated time until the battery is empty, projected from the drain since it was last unplugged with the screen on and off together, so it covers the whole charge rather than the last ten minutes. It falls between the times the screen-on and screen-off drain give.
_Avoid_: battery life, runtime.

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

**Unplug reminder**:
The opt-in repeat of the full-battery alert, re-posted every N minutes for as long as the battery stays on the charger at or above the charge target (default off, gap 15 min, range 5–60). It repeats the one alert rather than adding a second one, and the charger coming out is the only thing that ends it.
_Avoid_: nag, snooze, recurring alert, repeat notification.

**Notification details**:
What the status notification's collapsed line shows: up to three families the user picks from Speed, Current, Time, Temperature and Power source, in the order the user puts them.
_Avoid_: notification metrics, detail fields.

**Speed**:
The notification-details family for how fast the level is moving: the drain rate while discharging; while charging, the charge power, or the charge rate when the power cannot be estimated. A family name only, never a synonym for drain rate.

**Temperature range**:
The lowest and highest battery temperature seen since the battery last finished charging, in the
user's display unit. Accumulated from the readings the monitoring service observes; finishing a
charge is the only thing that starts a new range, and "finished" means the charge target — so on a
phone set to stop at 90% the range restarts there, not at 100%.
_Avoid_: temperature history, heat range, thermal range.

**Stable capacity**:
The learned running average of the measured full capacity (mAh), built from spaced, trusted
per-tick estimates. Slow-moving by design: it is the denominator of the sub-percent battery
display, so the charge counter's live movement shows up in the decimals instead of cancelling out.
Distinct from design capacity and from the single-tick estimate.
_Avoid_: averaged capacity, smoothed capacity.
