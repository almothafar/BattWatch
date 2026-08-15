package com.almothafar.simplebatterynotifier.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.LevelThresholds;

/**
 * Typed facade over the app's default {@link SharedPreferences} (#162): the single owner of each
 * migrated setting's <b>key + default + clamp</b>. Read sites call the typed accessor instead of
 * repeating {@code getInt(getString(R.string._pref_key_…), <inline default>)}, so a default can never
 * silently drift between the alert engine, the receiver and the UI.
 * <p>
 * <b>Migrated so far:</b>
 * <ul>
 *   <li>the critical/warning battery levels — the {@code 20}/{@code 40} literals that previously lived
 *       in {@code NotificationService}, {@code BatteryLevelReceiver}, {@code MainActivity} and the range
 *       slider's helper now derive from {@link #DEFAULT_CRITICAL_LEVEL} / {@link #DEFAULT_WARNING_LEVEL},
 *       and the pair travels as a {@link LevelThresholds};</li>
 *   <li>the shared "high drain" limit — its default, accepted range and clamp ({@link #drainLimitPph}
 *       + {@link #clampDrainLimit}) moved here from {@code BatteryRateTracker}, so "a corrupt stored
 *       value can't defeat the feature" lives in one place;</li>
 *   <li>the "Vibrate" flag ({@link #vibrateEnabled}) — previously re-read with an inline {@code true}
 *       default in three spots (channel creation, the level-alert config and the manual override path),
 *       which meant the alert channels and the silent-mode buzz could drift apart.</li>
 * </ul>
 * The one restatement that remains is the XML-declared slider default in {@code pref_alerts.xml}, which
 * the framework instantiates from XML and so cannot share a constant with — a comment ties the two, and
 * {@code AppPrefsTest} asserts they stay equal. Remaining settings migrate incrementally.
 */
public final class AppPrefs {

	/** Default critical battery level in percent — the single owner of this value (#162). */
	public static final int DEFAULT_CRITICAL_LEVEL = 20;
	/** Default warning battery level in percent — the single owner of this value (#162). */
	public static final int DEFAULT_WARNING_LEVEL = 40;

	/**
	 * Default charge target in percent — the level the full-battery alert fires at (#263). 90 matches the "unplug before 100%" advice the alert copy itself
	 * gives.
	 */
	public static final int DEFAULT_CHARGE_TARGET = 90;
	/**
	 * Lowest accepted charge target; mirrors the slider's {@code app:min} in pref_alerts.xml.
	 * <p>
	 * {@code app:min}, not {@code android:min}: androidx {@code SeekBarPreference} declares {@code min} in the library namespace, so {@code android:min} is
	 * read by nothing, raises no error and leaves the slider bottoming out at 0.
	 */
	public static final int MIN_CHARGE_TARGET = 80;
	/**
	 * Highest accepted charge target; mirrors the slider's {@code android:max} in pref_alerts.xml. At this value the alert waits for a genuinely complete
	 * charge, which is what the app did before the target existed.
	 */
	public static final int MAX_CHARGE_TARGET = 100;
	/**
	 * How far below the charge target the level must fall before an end-of-charge episode re-arms.
	 * <p>
	 * The full-battery alert fires once per charge session; without a margin it would re-arm on the very tick it fired and then fire again on every percent up
	 * to 100. At the maximum target this is the {@code ≤ 95} band the alert has always re-armed on.
	 */
	public static final int CHARGE_TARGET_REARM_MARGIN = 5;

	/** Default "high drain" limit in %/h. */
	public static final int DEFAULT_DRAIN_LIMIT_PPH = 20;
	/** Lowest accepted drain limit in %/h; mirrors the slider's {@code app:min} in pref_alerts.xml. */
	public static final int MIN_DRAIN_LIMIT_PPH = 5;
	/** Highest accepted drain limit in %/h; mirrors the slider's {@code android:max} in pref_alerts.xml. */
	public static final int MAX_DRAIN_LIMIT_PPH = 60;

	/** Default for the "Vibrate" preference — mirrors the switch's {@code android:defaultValue} in pref_behaviour.xml. */
	public static final boolean DEFAULT_VIBRATE = true;

	private AppPrefs() {
		// Utility class
	}

	/**
	 * The critical battery level in percent: the level at/below which the critical alert fires and the
	 * home gauge turns red. Falls back to {@link #DEFAULT_CRITICAL_LEVEL} when unset.
	 *
	 * @param context Application context
	 *
	 * @return the configured critical level
	 */
	public static int criticalLevel(Context context) {
		return prefs(context).getInt(context.getString(R.string._pref_key_critical_battery_level), DEFAULT_CRITICAL_LEVEL);
	}

	/**
	 * The warning battery level in percent: the level at/below which the warning alert fires and the
	 * home gauge turns amber. Falls back to {@link #DEFAULT_WARNING_LEVEL} when unset.
	 *
	 * @param context Application context
	 *
	 * @return the configured warning level
	 */
	public static int warningLevel(Context context) {
		return prefs(context).getInt(context.getString(R.string._pref_key_warn_battery_level), DEFAULT_WARNING_LEVEL);
	}

	/**
	 * Both battery-level thresholds as one value, so critical and warning travel together instead of as a
	 * loose (int, int) pair callers must keep in the right order.
	 *
	 * @param context Application context
	 *
	 * @return the configured {@code (critical, warning)} thresholds
	 */
	public static LevelThresholds batteryLevels(Context context) {
		return new LevelThresholds(criticalLevel(context), warningLevel(context));
	}

	/**
	 * Persist both battery-level thresholds together. The settings-screen slider and the home-screen
	 * in-fly slider both write this pair, so the write lives here beside the matching reads.
	 *
	 * @param context Application context
	 * @param levels  the thresholds to store
	 */
	public static void setBatteryLevels(Context context, LevelThresholds levels) {
		prefs(context).edit()
		              .putInt(context.getString(R.string._pref_key_critical_battery_level), levels.critical())
		              .putInt(context.getString(R.string._pref_key_warn_battery_level), levels.warning())
		              .apply();
	}

	/**
	 * The charge target in percent (#263): the level at which the full-battery alert fires while charging, instead of only when the battery reports a completed
	 * charge. Reads {@link #DEFAULT_CHARGE_TARGET} when unset and always {@link #clampChargeTarget clamps} the stored value, so a corrupt preference can't move
	 * the alert somewhere the slider can't reach.
	 *
	 * @param context Application context
	 *
	 * @return the configured charge target in percent
	 */
	public static int chargeTarget(Context context) {
		return clampChargeTarget(prefs(context).getInt(
				context.getString(R.string._pref_key_charge_target), DEFAULT_CHARGE_TARGET));
	}

	/**
	 * Clamps a stored charge target to {@code [MIN_CHARGE_TARGET, MAX_CHARGE_TARGET]}. The bounds mirror the slider's {@code app:min}/{@code android:max} in
	 * {@code pref_alerts.xml}. Pure so it is unit-testable.
	 *
	 * @param stored the raw persisted target in percent
	 *
	 * @return the target clamped to {@code [MIN_CHARGE_TARGET, MAX_CHARGE_TARGET]}
	 */
	public static int clampChargeTarget(int stored) {
		return Math.max(MIN_CHARGE_TARGET, Math.min(MAX_CHARGE_TARGET, stored));
	}

	/**
	 * Whether a charge target means "wait for a genuinely full battery" — the behaviour the app had before the target was configurable, and the one place that
	 * decides which wording the settings and the notification use ("full" vs "almost full", #263).
	 *
	 * @param chargeTarget the configured charge target in percent
	 *
	 * @return true when the target is a complete charge
	 */
	public static boolean targetIsAFullCharge(int chargeTarget) {
		return chargeTarget >= MAX_CHARGE_TARGET;
	}

	/**
	 * The level an end-of-charge episode re-arms at: {@code target − CHARGE_TARGET_REARM_MARGIN}. Shared by the full-battery alert's once-per-charge flag and
	 * the temperature range's per-charge reset (#260/#263), so the two agree on when a charge session has genuinely been left behind. Pure.
	 *
	 * @param chargeTarget the configured charge target in percent
	 *
	 * @return the level at/below which the episode re-arms
	 */
	public static int reArmLevel(int chargeTarget) {
		return chargeTarget - CHARGE_TARGET_REARM_MARGIN;
	}

	/**
	 * The user's shared "high drain" limit in %/h — the red line in the details table (#108) and the
	 * fast-drain alert trigger (#109). Reads {@link #DEFAULT_DRAIN_LIMIT_PPH} when unset and always
	 * {@link #clampDrainLimit clamps} the stored value, so a corrupt preference can't skew the red line
	 * or the alert trigger.
	 *
	 * @param context Application context
	 *
	 * @return the configured limit in %/h
	 */
	public static int drainLimitPph(Context context) {
		return clampDrainLimit(prefs(context).getInt(
				context.getString(R.string._pref_key_fast_drain_limit), DEFAULT_DRAIN_LIMIT_PPH));
	}

	/**
	 * Clamps a stored drain limit to {@code [MIN_DRAIN_LIMIT_PPH, MAX_DRAIN_LIMIT_PPH]}. The bounds mirror the slider's {@code app:min}/{@code android:max} in
	 * {@code pref_alerts.xml}. Pure so it is unit-testable.
	 *
	 * @param stored the raw persisted limit in %/h
	 *
	 * @return the limit clamped to {@code [MIN_DRAIN_LIMIT_PPH, MAX_DRAIN_LIMIT_PPH]}
	 */
	public static int clampDrainLimit(int stored) {
		return Math.max(MIN_DRAIN_LIMIT_PPH, Math.min(MAX_DRAIN_LIMIT_PPH, stored));
	}

	/**
	 * Whether the "Vibrate" preference is on (default {@link #DEFAULT_VIBRATE}). It drives both the alert
	 * channels' vibration and the manual silent-mode-override vibration, so those two reads can't disagree
	 * about whether to buzz.
	 *
	 * @param context Application context
	 *
	 * @return true when vibration is enabled
	 */
	public static boolean vibrateEnabled(Context context) {
		return prefs(context).getBoolean(context.getString(R.string._pref_key_notifications_vibrate), DEFAULT_VIBRATE);
	}

	private static SharedPreferences prefs(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context);
	}
}
