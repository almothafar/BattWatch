package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;

/**
 * Tracks the lowest and highest battery temperature seen since the battery last finished charging —
 * the <b>temperature range</b> shown on the Battery Insights screen (issue #260).
 * <p>
 * <b>Why per charge.</b> The temperature is read on every battery broadcast but was never retained,
 * so after the fact there was no way to tell whether the battery brushed the alert threshold for a
 * minute or spent the afternoon there. A range bounded by the charge the user just completed answers
 * that in the terms people already think in, and needs no history or database — just a running
 * minimum and maximum.
 * <p>
 * <b>When the range resets.</b> Only when a charge completes. Plugging in, unplugging and the clock
 * do nothing. "Charge complete" is {@link BatteryManager#BATTERY_STATUS_FULL} <em>or</em> a level of
 * {@link #FULL_LEVEL_PERCENT}: the status alone misses devices whose charge cap stops them short of
 * 100%, and the level alone misses OEMs that report full a percent early. The reset is edge-triggered
 * through {@link TemperatureStats#fullSeen()} — it fires once per completed charge and re-arms when
 * the battery leaves the full state, so sitting plugged in at full does not keep wiping the range.
 * <p>
 * <b>Storage.</b> The stats live in the backup-excluded transient file ({@link TransientState}):
 * another device's temperature range says nothing about this one, and a fresh install fills in on the
 * next broadcast. There is deliberately no sample count — it would change on every tick and defeat
 * the save-on-change rule the other trackers follow, so after {@code hasData} flips only a genuinely
 * wider range writes. The pure helpers carry the correctness and are unit-tested without Android,
 * mirroring {@link BatteryCapacityTracker}.
 */
public final class BatteryTemperatureTracker {

	// Persisted range state, kept in the backup-excluded transient file (TransientState, #167).
	private static final String PREF_MIN_TENTHS = "_temperature_min_tenths";
	private static final String PREF_MAX_TENTHS = "_temperature_max_tenths";
	private static final String PREF_HAS_DATA = "_temperature_has_data";
	private static final String PREF_FULL_SEEN = "_temperature_full_seen";

	/** The level at which a charge counts as complete even if the status never reports FULL. */
	static final int FULL_LEVEL_PERCENT = 100;
	// Plausibility band in tenths of a degree Celsius: -20 C to 100 C. A reading outside it is a
	// driver artefact, not a battery, and folding it in would freeze the range on a bogus extreme.
	static final int MIN_PLAUSIBLE_TENTHS_C = -200;
	static final int MAX_PLAUSIBLE_TENTHS_C = 1000;

	private BatteryTemperatureTracker() {
		// Utility class - prevent instantiation
	}

	/**
	 * Folds one battery reading into the persisted range. The single entry point, called from
	 * {@link com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver} on every
	 * {@code ACTION_BATTERY_CHANGED} broadcast.
	 *
	 * @param context      Application context
	 * @param rawTenthsC   battery temperature in tenths of a degree Celsius
	 * @param levelPercent the battery level as a whole percent
	 * @param status       the {@code BatteryManager} status extra from this broadcast
	 */
	public static void record(Context context, int rawTenthsC, int levelPercent, int status) {
		final SharedPreferences prefs = TransientState.prefs(context);
		final TemperatureStats previous = loadStats(prefs);
		final TemperatureStats updated = fold(previous, rawTenthsC, levelPercent, status);

		// Persist only on change: most broadcasts sit well inside the range already recorded, and
		// rewriting an identical state would churn SharedPreferences on every tick.
		if (!updated.equals(previous)) {
			saveStats(prefs, updated);
		}
	}

	/**
	 * Folds one reading into the range. Pure so it is unit-testable. A completed charge starts a fresh
	 * range at that reading; otherwise the reading widens the existing one. Implausible readings are
	 * rejected — the same instance returns, so the caller skips the persist.
	 *
	 * @param previous     the range so far
	 * @param rawTenthsC   battery temperature in tenths of a degree Celsius
	 * @param levelPercent the battery level as a whole percent
	 * @param status       the {@code BatteryManager} status extra from this broadcast
	 *
	 * @return the updated stats, or {@code previous} itself when the reading was rejected
	 */
	static TemperatureStats fold(TemperatureStats previous, int rawTenthsC, int levelPercent, int status) {
		// Exactly 0 is not "0.0 °C": SystemService reads EXTRA_TEMPERATURE with a default of 0, so on a
		// device that doesn't report a temperature every tick would look like a freezing battery.
		if (rawTenthsC == 0 || rawTenthsC < MIN_PLAUSIBLE_TENTHS_C || rawTenthsC > MAX_PLAUSIBLE_TENTHS_C) {
			return previous;
		}

		final boolean chargeComplete = isChargeComplete(levelPercent, status);
		if (chargeComplete && !previous.fullSeen()) {
			return new TemperatureStats(rawTenthsC, rawTenthsC, true, true);
		}
		// chargeComplete doubles as the new fullSeen: leaving the full state re-arms the next reset,
		// staying in it holds the flag so the range is not wiped again on the following tick.
		if (!previous.hasData()) {
			return new TemperatureStats(rawTenthsC, rawTenthsC, true, chargeComplete);
		}
		return new TemperatureStats(
				Math.min(previous.minTenthsC(), rawTenthsC),
				Math.max(previous.maxTenthsC(), rawTenthsC),
				true,
				chargeComplete);
	}

	/**
	 * Whether this broadcast says the charge has finished. Both signals count, because neither is
	 * reliable alone: a device with a charge cap can report FULL well below 100%, and some OEMs report
	 * 100% a tick before the status catches up.
	 *
	 * @param levelPercent the battery level as a whole percent
	 * @param status       the {@code BatteryManager} status extra from this broadcast
	 *
	 * @return true when the battery counts as fully charged
	 */
	static boolean isChargeComplete(int levelPercent, int status) {
		return status == BatteryManager.BATTERY_STATUS_FULL || levelPercent >= FULL_LEVEL_PERCENT;
	}

	/**
	 * Reads the recorded temperature range for display (#260).
	 *
	 * @param context Application context
	 *
	 * @return the range, or {@code null} before any usable reading has been recorded
	 */
	public static TemperatureRange getRange(Context context) {
		return summarize(loadStats(TransientState.prefs(context)));
	}

	/**
	 * The display view of the recorded stats. Pure so the empty boundary is unit-testable.
	 *
	 * @param stats the range so far
	 *
	 * @return the recorded range, or {@code null} when nothing has been recorded yet
	 */
	static TemperatureRange summarize(TemperatureStats stats) {
		if (!stats.hasData()) {
			return null;
		}
		return new TemperatureRange(stats.minTenthsC(), stats.maxTenthsC());
	}

	/**
	 * Loads the persisted stats; package-private so the state tests can assert what was stored.
	 */
	static TemperatureStats loadStats(SharedPreferences prefs) {
		return new TemperatureStats(
				prefs.getInt(PREF_MIN_TENTHS, 0),
				prefs.getInt(PREF_MAX_TENTHS, 0),
				prefs.getBoolean(PREF_HAS_DATA, false),
				prefs.getBoolean(PREF_FULL_SEEN, false));
	}

	/**
	 * Persists the stats; package-private so the state tests can seed a recorded range.
	 */
	static void saveStats(SharedPreferences prefs, TemperatureStats stats) {
		prefs.edit()
		     .putInt(PREF_MIN_TENTHS, stats.minTenthsC())
		     .putInt(PREF_MAX_TENTHS, stats.maxTenthsC())
		     .putBoolean(PREF_HAS_DATA, stats.hasData())
		     .putBoolean(PREF_FULL_SEEN, stats.fullSeen())
		     .apply();
	}

	/**
	 * The persistent temperature-range state.
	 *
	 * @param minTenthsC coldest reading this charge, in tenths of a degree Celsius
	 * @param maxTenthsC hottest reading this charge, in tenths of a degree Celsius
	 * @param hasData    whether any usable reading has been folded in (the min/max are meaningless
	 *                   until it is true)
	 * @param fullSeen   whether the current full state has already reset the range, so the reset
	 *                   fires once per completed charge rather than on every tick while full
	 */
	record TemperatureStats(int minTenthsC, int maxTenthsC, boolean hasData, boolean fullSeen) {
	}

	/**
	 * The read-only view of the recorded range for display (#260).
	 *
	 * @param minTenthsC coldest reading this charge, in tenths of a degree Celsius
	 * @param maxTenthsC hottest reading this charge, in tenths of a degree Celsius
	 */
	public record TemperatureRange(int minTenthsC, int maxTenthsC) {
	}
}
