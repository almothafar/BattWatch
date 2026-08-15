package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;

import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.util.AppPrefs;

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
 * <b>When the range resets.</b> Only when a charge completes. Plugging in, unplugging and the clock do nothing. "Charge complete" is
 * {@link BatteryManager#BATTERY_STATUS_FULL} <em>or</em> the user's charge target ({@link AppPrefs#chargeTarget}): the status alone misses devices whose charge
 * cap stops them short of the target, and the level alone misses OEMs that report full a percent early. Sharing the target with the full-battery alert (#263)
 * is what keeps one definition of "this charge is done": on a phone habitually unplugged at 90% a fixed 100% is never reached, and the range would grow for
 * weeks without ever starting over. The reset is edge-triggered through {@link TemperatureStats#fullSeen()} — it fires once per completed charge and re-arms
 * only once the battery has genuinely dropped out of the full band (see {@link #nextFullSeen}), so neither sitting plugged in at full nor the overnight top-up
 * cycle keeps wiping the range.
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

	// The range a completed charge starts over from. Only its min/max/hasData are ever read — fold
	// always supplies the reset flag itself.
	private static final TemperatureStats NO_RANGE = new TemperatureStats(0, 0, false, false);

	private BatteryTemperatureTracker() {
		// Utility class - prevent instantiation
	}

	/**
	 * Folds one battery reading into the persisted range. The single entry point, called from
	 * {@link com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver} on every
	 * {@code ACTION_BATTERY_CHANGED} broadcast.
	 *
	 * @param context   Application context
	 * @param batteryDO the reading from this broadcast — the temperature, the level and the status all
	 *                  matter, because finishing a charge is what starts a new range
	 */
	public static void record(Context context, BatteryDO batteryDO) {
		final SharedPreferences prefs = TransientState.prefs(context);
		final TemperatureStats previous = loadStats(prefs);
		final TemperatureStats updated = fold(
				previous,
				batteryDO.getTemperature(),
				batteryDO.getBatteryPercentageInt(),
				batteryDO.getStatus(),
				AppPrefs.chargeTarget(context));

		// Persist only on change: most broadcasts sit well inside the range already recorded, and
		// rewriting an identical state would churn SharedPreferences on every tick.
		if (!updated.equals(previous)) {
			saveStats(prefs, updated);
		}
	}

	/**
	 * Folds one reading into the range. Pure so it is unit-testable. A completed charge starts a fresh
	 * range; otherwise the reading widens the existing one. A broadcast that carried no temperature
	 * ({@link #isUnreported}) leaves the min/max alone but still moves the reset flag.
	 *
	 * @param previous     the range so far
	 * @param rawTenthsC   battery temperature in tenths of a degree Celsius
	 * @param levelPercent the battery level as a whole percent
	 * @param status       the {@code BatteryManager} status extra from this broadcast
	 * @param chargeTarget the user's charge target — the level a charge counts as finished at (#263)
	 *
	 * @return the range to persist, value-equal to {@code previous} when nothing changed
	 */
	static TemperatureStats fold(
			TemperatureStats previous,
			int rawTenthsC,
			int levelPercent,
			int status,
			int chargeTarget) {
		// The end-of-charge bookkeeping is decided before the reading is judged. Deciding it after
		// would couple the two: on a device that reports no temperature the flag could never move, so
		// the reset would stay stuck at whatever a fresh install left it on.
		final boolean chargeComplete = isChargeComplete(levelPercent, status, chargeTarget);
		final boolean fullSeen = nextFullSeen(previous, levelPercent, chargeComplete, chargeTarget);
		final TemperatureStats base = chargeComplete && previous.resetArmed() ? NO_RANGE : previous;

		if (isUnreported(rawTenthsC)) {
			return new TemperatureStats(base.minTenthsC(), base.maxTenthsC(), base.hasData(), fullSeen);
		}
		if (base.isEmpty()) {
			return new TemperatureStats(rawTenthsC, rawTenthsC, true, fullSeen);
		}
		return new TemperatureStats(
				Math.min(base.minTenthsC(), rawTenthsC),
				Math.max(base.maxTenthsC(), rawTenthsC),
				true,
				fullSeen);
	}

	/**
	 * Whether this broadcast carried no temperature at all. Exactly 0 is not "0.0 °C": {@link
	 * SystemService} reads {@code EXTRA_TEMPERATURE} with a default of 0, so on a device that doesn't
	 * report one every tick would look like a freezing battery. Only 0 is filtered — a genuinely
	 * alarming reading is the single most useful thing this card can show, so nothing else is second-
	 * guessed here.
	 *
	 * @param rawTenthsC battery temperature in tenths of a degree Celsius
	 *
	 * @return true when the reading is the "not reported" default
	 */
	static boolean isUnreported(int rawTenthsC) {
		return rawTenthsC == 0;
	}

	/**
	 * The reset flag to carry into the next broadcast. Set the moment a charge completes; cleared only once the battery has genuinely dropped out of the full
	 * band ({@link AppPrefs#reArmLevel}).
	 * <p>
	 * Re-arming on a bare "no longer full" would fire the reset repeatedly overnight: a phone left on the charger drifts 100 → 99 (status {@code CHARGING}) and
	 * tops back up again and again, and each of those dips would re-arm, so by morning the range would span the last few minutes instead of the night. The
	 * drop-out band is the one the full-battery alert already re-arms on ({@code fullNotified} in {@code BatteryLevelReceiver}), which is why both read it from
	 * the same place. It also holds the flag on charge-capped devices, which report FULL well below the target and would otherwise re-arm on their own resting
	 * level and reset on every following tick.
	 *
	 * @param previous       the range so far
	 * @param levelPercent   the battery level as a whole percent
	 * @param chargeComplete whether this broadcast says the charge has finished
	 * @param chargeTarget   the user's charge target in percent
	 *
	 * @return the new {@code fullSeen} flag
	 */
	static boolean nextFullSeen(TemperatureStats previous, int levelPercent, boolean chargeComplete, int chargeTarget) {
		if (chargeComplete) {
			return true;
		}
		return levelPercent > AppPrefs.reArmLevel(chargeTarget) && previous.fullSeen();
	}

	/**
	 * Whether this broadcast says the charge has finished. Both signals count, because neither is reliable alone: a device with a charge cap can report FULL
	 * well below the target, and some OEMs report the level a tick before the status catches up.
	 *
	 * @param levelPercent the battery level as a whole percent
	 * @param status       the {@code BatteryManager} status extra from this broadcast
	 * @param chargeTarget the user's charge target — the level a charge counts as finished at (#263)
	 *
	 * @return true when the charge counts as done
	 */
	static boolean isChargeComplete(int levelPercent, int status, int chargeTarget) {
		return status == BatteryManager.BATTERY_STATUS_FULL || levelPercent >= chargeTarget;
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
		if (stats.isEmpty()) {
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

		/**
		 * Whether no usable reading has been folded in yet, so there is no range to widen. Named for
		 * how it is read — every call site asks about the empty state, and {@code !hasData()} is
		 * exactly the always-inverted call CODE_REVIEW_GUIDELINES.md asks us to name away.
		 *
		 * @return true when the min/max mean nothing yet
		 */
		boolean isEmpty() {
			return !hasData;
		}

		/**
		 * Whether a completed charge would still reset the range. The inverse of {@link #fullSeen()},
		 * named for its one reader for the same reason as {@link #isEmpty()}.
		 *
		 * @return true when the next completed charge starts a fresh range
		 */
		boolean resetArmed() {
			return !fullSeen;
		}
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
