package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.os.BatteryManager;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.model.ChargeSpeed;
import com.almothafar.simplebatterynotifier.model.DetailFamily;
import com.almothafar.simplebatterynotifier.model.PowerSource;
import com.almothafar.simplebatterynotifier.util.AppPrefs;
import com.almothafar.simplebatterynotifier.util.BatteryPercentFormatter;
import com.almothafar.simplebatterynotifier.util.TemperatureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.almothafar.simplebatterynotifier.util.BidiText.isolate;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Builds the text of the persistent (ongoing) battery-status notification (issue #166): its stable
 * title, the collapsed detail line and the expanded (BigText) breakdown, plus the small icon that
 * reflects the current battery state.
 * <p>
 * Split out of {@code NotificationService} — which now only wires this text into a {@code Notification} —
 * so the AccuBattery-style status layout (#194) lives in one place and stays directly unit-testable
 * ({@code OngoingStatusContentTest}). The RTL bidi-isolation this file introduced for #194 now lives in
 * {@link com.almothafar.simplebatterynotifier.util.BidiText}, shared with the alert copy that needs it too (#275).
 */
public final class OngoingStatusContent {

	/** Joins the ongoing notification's detail segments (rate/power · current · time), and the family names that summarise a choice of them. */
	public static final String DETAIL_SEPARATOR = " · ";

	/**
	 * One rung of a level→icon ladder: the lowest battery level that {@code iconRes} covers. The two
	 * ladders below are the single source of truth for the band boundaries (#235) — nothing else, in code
	 * or in the drawables' own comments, restates them.
	 *
	 * @param minLevel the lowest level, in percent, that this icon covers
	 * @param iconRes  the small-icon drawable for that band
	 */
	private record IconBand(int minLevel, int iconRes) {
	}

	/**
	 * Discharging: Material's {@code battery_0_bar}…{@code battery_6_bar} plus {@code battery_full}, in
	 * even ~14-point bands so each bar covers a comparable slice. Only a true 100% reaches the solid full
	 * battery, so "nearly full" never reads as "done".
	 */
	private static final List<IconBand> DISCHARGING_LADDER = List.of(
			new IconBand(100, R.drawable.ic_stat_battery_full),
			new IconBand(85, R.drawable.ic_stat_battery_6_bar),
			new IconBand(70, R.drawable.ic_stat_battery_5_bar),
			new IconBand(55, R.drawable.ic_stat_battery_4_bar),
			new IconBand(40, R.drawable.ic_stat_battery_3_bar),
			new IconBand(25, R.drawable.ic_stat_battery_2_bar),
			new IconBand(10, R.drawable.ic_stat_battery_1_bar),
			new IconBand(0, R.drawable.ic_stat_battery_0_bar));

	/**
	 * Charging: Material's {@code battery_charging_20/30/50/60/80/90} plus {@code battery_charging_full}.
	 * The numbers in those names are Material's own labels for how full the <em>artwork</em> looks, not
	 * thresholds — the floors here are chosen for how each icon reads, so e.g. {@code charging_60} starts
	 * at 55% and {@code charging_90} at 88% (#235).
	 */
	private static final List<IconBand> CHARGING_LADDER = List.of(
			new IconBand(98, R.drawable.ic_stat_battery_charging_full),
			new IconBand(88, R.drawable.ic_stat_battery_charging_90),
			new IconBand(73, R.drawable.ic_stat_battery_charging_80),
			new IconBand(55, R.drawable.ic_stat_battery_charging_60),
			new IconBand(40, R.drawable.ic_stat_battery_charging_50),
			new IconBand(25, R.drawable.ic_stat_battery_charging_30),
			new IconBand(0, R.drawable.ic_stat_battery_charging_20));

	private OngoingStatusContent() {
		// Utility class - prevent instantiation
	}

	/**
	 * Build the title line of the ongoing status notification: the stable headline "85% · Discharging".
	 * <p>
	 * The title carries the two most stable, always-available metrics — the live percentage and the
	 * plain charge state — not the app name (Android already prints that in the header) and not the
	 * volatile rate/time (those live in the detail line, so the title and body never repeat each other).
	 * The percentage is the same live value the home gauge shows: two decimals when the device genuinely
	 * resolves below one percent, whole otherwise (#158).
	 *
	 * @param context   The application context
	 * @param batteryDO Current battery snapshot, or null if unavailable
	 * @return Formatted title text
	 */
	static String statusTitle(Context context, BatteryDO batteryDO) {
		// Isolated like every other Latin value here, though this one is defensive rather than load-bearing: the percentage opens the string, so no strong
		// character precedes it and the algorithm never retypes its digits — deleting the call changes nothing on screen today (#275). It earns its keep the
		// moment the title grows a word in front of the number, which is the shape every other surface in this class already has.
		final String percentage = isolate(BatteryPercentFormatter.formatLive(batteryDO));
		final String statusLabel = SystemService.getStatusLabel(context, isNull(batteryDO) ? -1 : batteryDO.getStatus());
		return context.getString(R.string.notification_status_title, percentage, statusLabel);
	}

	/**
	 * The collapsed content line — the volatile numbers under the stable title: the user's chosen notification details (#357), in their order, joined by
	 * "{@value #DETAIL_SEPARATOR}". The default is "9%/h · Using 250 mA · ~9h 27m remaining" while discharging and "~18 W · Charging 1500 mA · ~45m to full"
	 * while charging (#194, #207).
	 *
	 * @param context   The application context
	 * @param batteryDO Current battery snapshot, or null if unavailable
	 * @param rate      The precomputed charge/drain rate, or null if unavailable
	 * @return Formatted collapsed text (empty when there's no snapshot at all)
	 */
	static String statusDetail(Context context, BatteryDO batteryDO, BatteryRateTracker.BatteryRate rate) {
		if (isNull(batteryDO)) {
			return "";
		}
		final BatteryRateTracker.BatteryRate rateOrEmpty = nonNull(rate) ? rate : BatteryRateTracker.BatteryRate.empty();
		return detailLine(
				context,
				AppPrefs.notificationDetails(context),
				family -> detailSegment(context, family, batteryDO, rateOrEmpty),
				family -> isUnavailable(family, batteryDO, rateOrEmpty));
	}

	/**
	 * The collapsed line a choice makes from its families' segments — the one rule the notification and the picker's preview (#358) both render by.
	 * <p>
	 * A family with nothing to show yet drops out silently. When a non-empty choice shows nothing at all, the line says why rather than go blank: "Not
	 * available" when none of it is coming, otherwise "Calculating…", as the details table does (#216). No family stands in, since any of them is one the user
	 * can choose, and showing it unbidden would override a choice that left it out (#359). An empty choice renders an empty line: the user asked for the title
	 * alone.
	 *
	 * @param context     the context to word the line in
	 * @param selection   the chosen families, in the order the line shows them
	 * @param segmentOf   each family's segment, or null when it has nothing to show yet
	 * @param unavailable whether a family with no segment has none coming ({@link #isUnavailable})
	 * @return the line
	 */
	public static String detailLine(
			Context context,
			List<DetailFamily> selection,
			Function<DetailFamily, String> segmentOf,
			Predicate<DetailFamily> unavailable) {
		final List<String> parts = new ArrayList<>(selection.size());
		for (final DetailFamily family : selection) {
			addIfPresent(parts, segmentOf.apply(family));
		}
		if (parts.isEmpty() && !selection.isEmpty()) {
			final boolean noneComing = selection.stream().allMatch(unavailable);
			return context.getString(noneComing ? R.string.notification_status_unavailable : R.string.notification_status_calculating);
		}
		return String.join(DETAIL_SEPARATOR, parts);
	}

	/**
	 * One family's segment of the collapsed line, in the form the charge direction calls for (#356). Each is worded to read on its own, because any of them can
	 * end up alone on the line (#207). The picker's live-value column shows the same segments, through {@link NotificationService#liveStatus}.
	 *
	 * @param context   The application context
	 * @param family    The family to render
	 * @param batteryDO Current battery snapshot
	 * @param rate      The precomputed charge/drain rate
	 * @return the segment, bidi-isolated where it carries a Latin value; null when the family has nothing to show yet
	 */
	static String detailSegment(Context context, DetailFamily family, BatteryDO batteryDO, BatteryRateTracker.BatteryRate rate) {
		return switch (family) {
			case SPEED -> rateOrPowerSegment(context, batteryDO, rate);
			case CURRENT -> collapsedCurrentSegment(context, rate);
			case TIME -> collapsedTimeSegment(context, batteryDO, rate);
			case TEMPERATURE -> temperatureSegment(context, batteryDO);
			case POWER_SOURCE -> isolate(powerSourceSegment(context, batteryDO));
		};
	}

	/**
	 * Whether a family with nothing to show is unavailable rather than late: its value is not coming, so the picker's live-value column reads "—" for it, and
	 * a line of nothing else "Not available", rather than "Calculating…" (#359). The details table splits Time the same way (#216); its Current row never
	 * learned the flag below, so it keeps "calculating" there. Asked only of a family whose {@link #detailSegment} is null.
	 * <p>
	 * A full battery has none of them coming: its level no longer moves, so no rate settles, and nothing is left to time. Telling that apart from a warm-up is
	 * what keeps a phone left on the charger from reading "Calculating…" all night.
	 *
	 * @param family    The family with nothing to show
	 * @param batteryDO Current battery snapshot
	 * @param rate      The precomputed charge/drain rate
	 * @return true when nothing is coming until the battery's state changes
	 */
	static boolean isUnavailable(DetailFamily family, BatteryDO batteryDO, BatteryRateTracker.BatteryRate rate) {
		final boolean full = isFull(batteryDO);
		return switch (family) {
			case CURRENT -> full || rate.isCurrentUnavailable();
			// With a rate in, a missing estimate means there is no time left to estimate: the battery is empty, or one rounding step from it.
			case TIME -> full || rate.hasEstimateRate();
			// Speed goes missing wherever the level stops moving, and short of a full battery nothing tells that apart from a rate still settling.
			case SPEED -> full;
			// Both always have a reading, so neither is ever asked.
			case TEMPERATURE, POWER_SOURCE -> false;
		};
	}

	/**
	 * Whether the battery is full: reported so, or still charging at 100%, as some phones report before they say full. Many phones also keep reporting full
	 * for a while after the cable comes out (see {@code BatteryLevelReceiver}), when the level has not moved yet either.
	 */
	private static boolean isFull(BatteryDO batteryDO) {
		return batteryDO.getStatus() == BatteryManager.BATTERY_STATUS_FULL
				|| (batteryDO.getStatus() == BatteryManager.BATTERY_STATUS_CHARGING && batteryDO.getBatteryPercentageInt() >= 100);
	}

	/**
	 * Whether the pull-down says anything the collapsed line doesn't, and so earns an expand chevron (#194). The labelled breakdown always does. On a warm-up
	 * tick the pull-down is the temperature alone, which adds something only when the line leaves the temperature out: a choice without Temperature, whether
	 * the line shows another family (#357) or says why it shows none (#359).
	 *
	 * @param context   The application context
	 * @param batteryDO Current battery snapshot, or null if unavailable
	 * @param collapsed The collapsed line, from {@link #statusDetail}
	 * @param expanded  The pull-down, from {@link #statusDetailExpanded}
	 * @return true when the notification should offer the pull-down
	 */
	static boolean pullDownAddsToLine(Context context, BatteryDO batteryDO, String collapsed, String expanded) {
		if (expanded.indexOf('\n') >= 0) {
			return true;
		}
		return !expanded.isEmpty() && !collapsed.contains(temperatureSegment(context, batteryDO));
	}

	/**
	 * The expanded content (BigText) — the same numbers on labelled lines (#194): Now / Average / Time&nbsp;remaining / Temperature while discharging, Now /
	 * Average / Time&nbsp;to&nbsp;full / Temperature while charging. "Now" is the instantaneous current (plus the charge wattage while charging); "Average" is
	 * the windowed average, carrying the smoothed %/h while discharging — the app computes a single smoothed rate, which is itself an average, so there is no
	 * separate instantaneous %/h. On battery, the screen-on and screen-off drain follow Average: the drain since the last unplug split by screen state
	 * (#193), each line appearing once its own state has warmed up, and ending in the time the battery would last in that state. The time remaining projects
	 * from both states together (#354), so it falls between those two times. Every line is dropped when its data is absent; the temperature always shows.
	 * Returns a single line when nothing but temperature is available, which {@link #pullDownAddsToLine} offers only where the line leaves it out.
	 * <p>
	 * Deliberately blind to the notification details the collapsed line follows: this is the everything view, which is what makes the collapsed line's silent
	 * drop-outs acceptable (docs/adr/0002). It is blind to the show-rate switch #357 removed for the same reason, though that switch used to hide its rate
	 * lines: only what the switch did to the collapsed line carries over ({@link AppPrefs#notificationDetails}).
	 *
	 * @param context   The application context
	 * @param batteryDO Current battery snapshot, or null if unavailable
	 * @param rate      The precomputed charge/drain rate
	 * @return newline-joined expanded text (may be a single line or empty)
	 */
	static String statusDetailExpanded(Context context, BatteryDO batteryDO, BatteryRateTracker.BatteryRate rate) {
		final List<String> lines = new ArrayList<>(6);
		if (nonNull(batteryDO) && nonNull(rate)) {
			addLine(context, lines, R.string.notification_label_now, nowSegment(context, batteryDO, rate));
			addAverageLine(context, lines, rate);
			final int level = batteryDO.getBatteryPercentageInt();
			addLine(context, lines, R.string.screen_on_drain, ScreenDrainTracker.formatValue(context, rate.screenSplit().on(), level));
			addLine(context, lines, R.string.screen_off_drain, ScreenDrainTracker.formatValue(context, rate.screenSplit().off(), level));
			addTimeLine(context, lines, level, rate);
		}
		if (nonNull(batteryDO)) {
			addLine(context, lines, R.string.temperature, TemperatureUtils.format(context, batteryDO.getTemperature()));
		}
		return String.join("\n", lines);
	}

	/**
	 * Choose a small icon for the ongoing notification that mirrors the charge level, like the system
	 * battery icon (#235): a bolt-bearing battery while actively charging, a plain filling battery
	 * otherwise. (A charged-but-still-plugged battery reads as full, without a bolt.)
	 * <p>
	 * Granularity earns its keep here and only here — the persistent notification sits in the shade long
	 * enough to read as a live gauge. The transient alerts stay <em>semantic</em> (alert / low / full /
	 * error), because a level gauge says nothing about why they fired.
	 * <p>
	 * With no snapshot at all the icon says so ({@code battery_unknown}, a "?" badge) rather than picking
	 * a level it cannot know — the old fallback drew a <em>full</em> battery, which read as a confident
	 * 100% at the one moment the app has nothing to report.
	 *
	 * @param batteryDO Current battery snapshot, or null if unavailable
	 * @return Drawable resource id
	 */
	static int ongoingIconRes(BatteryDO batteryDO) {
		if (isNull(batteryDO)) {
			return R.drawable.ic_stat_battery_unknown;
		}
		final List<IconBand> ladder = batteryDO.getStatus() == BatteryManager.BATTERY_STATUS_CHARGING
		                              ? CHARGING_LADDER
		                              : DISCHARGING_LADDER;
		return iconForLevel(ladder, batteryDO.getBatteryPercentageInt());
	}

	/**
	 * The icon for the first band the level reaches. Ladders run highest floor first and end at a zero
	 * floor, so any real level (0–100) matches inside the loop; the trailing return is the floor icon,
	 * reached only by a nonsensical negative level.
	 *
	 * @param ladder the level→icon bands, highest floor first
	 * @param level  the battery level in percent
	 * @return Drawable resource id
	 */
	private static int iconForLevel(List<IconBand> ladder, int level) {
		for (IconBand band : ladder) {
			if (level >= band.minLevel()) {
				return band.iconRes();
			}
		}
		return ladder.get(ladder.size() - 1).iconRes();
	}

	private static void addIfPresent(List<String> parts, String value) {
		if (nonNull(value)) {
			parts.add(value);
		}
	}

	/** Appends an expanded "label: value" line, with the (Latin) value bidi-isolated for RTL (#194). */
	private static void addLine(Context context, List<String> lines, int labelRes, String rawValue) {
		if (nonNull(rawValue)) {
			lines.add(context.getString(R.string.notification_detail_line, context.getString(labelRes), isolate(rawValue)));
		}
	}

	/**
	 * The "Average" expanded line: the windowed-average current, with the smoothed %/h appended while
	 * discharging (the %/h is itself a windowed average). When the average current isn't ready yet but a
	 * rate is, a plain "Drain rate" line stands in so the %/h isn't lost from the breakdown.
	 */
	private static void addAverageLine(Context context, List<String> lines, BatteryRateTracker.BatteryRate rate) {
		if (rate.hasAvgCurrent()) {
			String value = BatteryRateTracker.formatCurrentValue(context, rate.avgCurrentMilliAmps());
			if (!rate.charging() && rate.hasRate()) {
				value = value + DETAIL_SEPARATOR + BatteryRateTracker.formatRateValue(context, rate.percentPerHour());
			}
			addLine(context, lines, R.string.notification_label_average, value);
		} else if (!rate.charging() && rate.hasRate()) {
			addLine(context, lines, R.string.drain_rate, BatteryRateTracker.formatRateValue(context, rate.percentPerHour()));
		}
	}

	/** The expanded time line: "Time remaining"/"Time to full" label with the bare duration (#194). */
	private static void addTimeLine(Context context, List<String> lines, int level, BatteryRateTracker.BatteryRate rate) {
		final int minutes = rate.estimatedMinutes(level);
		if (minutes > 0) {
			addLine(context, lines, rate.charging() ? R.string.time_to_full : R.string.time_remaining, BatteryRateTracker.formatDuration(context, minutes));
		}
	}

	/**
	 * The "Now" value: the instantaneous current, plus the charge wattage while charging (wattage alone if
	 * the current itself is unavailable). Null when neither is available.
	 */
	private static String nowSegment(Context context, BatteryDO batteryDO, BatteryRateTracker.BatteryRate rate) {
		if (rate.charging()) {
			final String power = powerSegment(context, batteryDO);
			if (rate.hasCurrent()) {
				final String current = BatteryRateTracker.formatCurrentValue(context, rate.currentMilliAmps());
				return nonNull(power) ? current + DETAIL_SEPARATOR + power : current;
			}
			return power;
		}
		return rate.hasCurrent() ? BatteryRateTracker.formatCurrentValue(context, rate.currentMilliAmps()) : null;
	}

	/**
	 * The collapsed rate/power segment (bidi-isolated): the drain rate "%/h" while discharging, or the
	 * charge power "~18 W" while charging (falling back to the charge %/h when the wattage is unknown).
	 * The raw current is <em>not</em> a fallback here — the collapsed line carries the current in its own
	 * segment, so this never duplicates it. Returns null when no rate/power is available.
	 */
	private static String rateOrPowerSegment(Context context, BatteryDO batteryDO, BatteryRateTracker.BatteryRate rate) {
		if (rate.charging()) {
			final String power = powerSegment(context, batteryDO);
			if (nonNull(power)) {
				return isolate(power);
			}
		}
		return rate.hasRate() ? isolate(BatteryRateTracker.formatRateValue(context, rate.percentPerHour())) : null;
	}

	/**
	 * The collapsed current segment, worded so it reads clearly even when it's the only surviving segment
	 * (#207): "Using 199 mA" while discharging, "Charging 1500 mA" while charging. The verb carries the
	 * direction, so the value is shown unsigned; the (Latin) magnitude is bidi-isolated inside the worded
	 * phrase (like the time segment) so it can't reorder in an RTL line. Null when no trustworthy current is
	 * available. The expanded "Live" line keeps the precise signed value under its label.
	 */
	private static String collapsedCurrentSegment(Context context, BatteryRateTracker.BatteryRate rate) {
		if (!rate.hasCurrent()) {
			return null;
		}
		final String magnitude = isolate(context.getString(R.string.battery_current_value,
				String.valueOf(Math.abs(rate.currentMilliAmps()))));
		return context.getString(rate.charging()
				? R.string.notification_status_current_charging
				: R.string.notification_status_current_using, magnitude);
	}

	/**
	 * The collapsed time segment: "~9h 27m remaining" / "~45m to full", with the duration bidi-isolated so
	 * it doesn't reorder inside an RTL line. Null when no non-degenerate estimate is available.
	 */
	private static String collapsedTimeSegment(Context context, BatteryDO batteryDO, BatteryRateTracker.BatteryRate rate) {
		final int minutes = rate.estimatedMinutes(batteryDO.getBatteryPercentageInt());
		if (minutes <= 0) {
			return null;
		}
		final String duration = isolate(BatteryRateTracker.formatDuration(context, minutes));
		return context.getString(rate.charging()
				? R.string.notification_status_time_to_full
				: R.string.notification_status_time_remaining, duration);
	}

	/** The collapsed temperature segment, which always has a reading. */
	private static String temperatureSegment(Context context, BatteryDO batteryDO) {
		return isolate(TemperatureUtils.format(context, batteryDO.getTemperature()));
	}

	/**
	 * The collapsed power-source segment: "On battery" while unplugged, otherwise the charger type. Shorter than the details table's labels ("USB Port",
	 * "AC Charger"), which have a row to themselves; this one shares the line with up to two others. Both decode the plugged reading through
	 * {@link PowerSource}, so they never disagree about which source it is.
	 */
	private static String powerSourceSegment(Context context, BatteryDO batteryDO) {
		return context.getString(switch (PowerSource.fromPlugged(batteryDO.getPlugged())) {
			case BATTERY -> R.string.notification_status_source_battery;
			case USB -> R.string.notification_status_source_usb;
			case AC -> R.string.notification_status_source_ac;
			case WIRELESS -> R.string.notification_status_source_wireless;
			case OTHER -> R.string.notification_status_source_other;
		});
	}

	/**
	 * The charge power as a wattage-only segment ("~18 W"), or null when it can't be estimated or rounds
	 * below 1 W. Derived from the snapshot (not a fresh read), so it agrees with the details table and
	 * {@link SlowChargeDetector} within a tick (#157). The tier label ("Fast charging") is deliberately
	 * omitted — it would repeat the "Charging" the title already carries.
	 *
	 * @param context   The application context
	 * @param batteryDO Current battery snapshot (non-null; the caller already checked)
	 * @return the formatted wattage segment, or null when the charge power is unknown or sub-watt
	 */
	private static String powerSegment(Context context, BatteryDO batteryDO) {
		final ChargeSpeed speed = ChargeSpeed.fromMeasurements(batteryDO.getCurrentMicroAmps(), batteryDO.getVoltage());
		if (!speed.isKnown() || speed.getWatts() < 1) {
			return null;
		}
		// Western digits (0-9) in every locale (#96).
		return context.getString(R.string.notification_status_watts, String.valueOf(speed.getWatts()));
	}

}
