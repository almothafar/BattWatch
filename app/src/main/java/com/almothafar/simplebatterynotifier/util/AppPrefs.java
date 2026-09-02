package com.almothafar.simplebatterynotifier.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.LevelThresholds;

import static java.util.Objects.isNull;

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
 *       which meant the alert channels and the silent-mode buzz could drift apart;</li>
 *   <li>the fast-drain timing pair (#109) — the sustained window and the reminder gap ({@link #fastDrainSustainedMs} + {@link #fastDrainReminderGapMs}), whose
 *       six bounds moved here from {@code FastDrainDetector} so they sit beside the {@link #clampMinutesToMs} that enforces them, like every other timing
 *       preference.</li>
 *   <li>the theme choice (#332) — its stored values ({@link #THEME_SYSTEM} / {@link #THEME_LIGHT} / {@link #THEME_DARK}) and the mapping to the
 *       {@code AppCompatDelegate} night modes ({@link #themeMode} + {@link #themeModeOf}), so the picker, {@code BattWatchApplication} and the fallback for a
 *       corrupt value all read the same table.</li>
 * </ul>
 * The restatements that remain are the defaults the framework instantiates straight from XML and so cannot share a constant with: each slider's in
 * {@code pref_alerts.xml} and the theme picker's in {@code pref_general.xml}. A comment ties each pair, and {@code AppPrefsTest} asserts they stay equal.
 * Remaining settings migrate incrementally; new ones (the
 * charge target #263, the unplug reminder #264) are born here, along with the one value here that is no setting at all: the pending "monitoring stopped"
 * report (#302).
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

	/**
	 * Default for the "remind me until I unplug" preference (#264) — <b>off</b>. The full-battery alert fires once per charge; the repeat is deliberately
	 * opt-in, because a notification that keeps coming back is a nag for everyone who did not ask for it.
	 */
	public static final boolean DEFAULT_UNPLUG_REMINDER = false;
	/** Default gap between unplug reminders, in minutes — mirrors the slider's {@code android:defaultValue} in pref_alerts.xml. */
	public static final int DEFAULT_UNPLUG_REMINDER_MINUTES = 15;
	/** Shortest accepted unplug-reminder gap; mirrors the slider's {@code app:min} in pref_alerts.xml. */
	public static final int MIN_UNPLUG_REMINDER_MINUTES = 5;
	/** Longest accepted unplug-reminder gap; mirrors the slider's {@code android:max} in pref_alerts.xml. */
	public static final int MAX_UNPLUG_REMINDER_MINUTES = 60;

	/** Default "high drain" limit in %/h. */
	public static final int DEFAULT_DRAIN_LIMIT_PPH = 20;
	/** Lowest accepted drain limit in %/h; mirrors the slider's {@code app:min} in pref_alerts.xml. */
	public static final int MIN_DRAIN_LIMIT_PPH = 5;
	/** Highest accepted drain limit in %/h; mirrors the slider's {@code android:max} in pref_alerts.xml. */
	public static final int MAX_DRAIN_LIMIT_PPH = 60;

	/**
	 * Default fast-drain window in minutes (#109) — how long the rate must hold at/above the limit before the alert fires. Mirrors the slider's
	 * {@code android:defaultValue} in pref_alerts.xml.
	 */
	public static final int DEFAULT_FAST_DRAIN_SUSTAINED_MINUTES = 5;
	/** Shortest accepted fast-drain window; mirrors the slider's {@code app:min} in pref_alerts.xml. */
	public static final int MIN_FAST_DRAIN_SUSTAINED_MINUTES = 1;
	/** Longest accepted fast-drain window; mirrors the slider's {@code android:max} in pref_alerts.xml. */
	public static final int MAX_FAST_DRAIN_SUSTAINED_MINUTES = 30;

	/** Default gap between fast-drain reminders, in minutes (#109) — mirrors the slider's {@code android:defaultValue} in pref_alerts.xml. */
	public static final int DEFAULT_FAST_DRAIN_REMINDER_MINUTES = 15;
	/** Shortest accepted fast-drain reminder gap; mirrors the slider's {@code app:min} in pref_alerts.xml. */
	public static final int MIN_FAST_DRAIN_REMINDER_MINUTES = 5;
	/** Longest accepted fast-drain reminder gap; mirrors the slider's {@code android:max} in pref_alerts.xml. */
	public static final int MAX_FAST_DRAIN_REMINDER_MINUTES = 60;

	/** Default for the "Vibrate" preference — mirrors the switch's {@code android:defaultValue} in pref_behaviour.xml. */
	public static final boolean DEFAULT_VIBRATE = true;

	/**
	 * The stored theme values written by the Settings picker (#332) — mirror the {@code theme_values} array in arrays.xml. Constants so the XML, the
	 * {@link #themeModeOf} mapping and its test all name the same three strings.
	 */
	public static final String THEME_SYSTEM = "system";
	/** Stored value for the always-light theme. */
	public static final String THEME_LIGHT = "light";
	/** Stored value for the always-dark theme. */
	public static final String THEME_DARK = "dark";

	/** Milliseconds in a minute — the conversion between the minutes every timing slider speaks in and the millis the decision cores measure in. */
	private static final long MS_PER_MINUTE = 60_000L;

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
	 * Whether the unplug reminder is on (#264): the opt-in repeat of the full-battery alert for as long as the battery stays on the charger at or above the
	 * charge target. Defaults to {@link #DEFAULT_UNPLUG_REMINDER} — the alert is once per charge unless the user asks otherwise.
	 *
	 * @param context Application context
	 *
	 * @return true when the full-battery alert repeats until the charger comes out
	 */
	public static boolean unplugReminderEnabled(Context context) {
		return prefs(context).getBoolean(context.getString(R.string._pref_key_notify_unplug_reminder), DEFAULT_UNPLUG_REMINDER);
	}

	/**
	 * The minimum gap between unplug reminders, in milliseconds (#264). Reads {@link #DEFAULT_UNPLUG_REMINDER_MINUTES} when unset and always clamps, so a
	 * corrupt preference can't turn the reminder into a notification on every broadcast.
	 * <p>
	 * A <em>minimum</em>, not a period: the reminder rides the battery broadcasts rather than a timer of its own (no alarm, no wakelock — the same design as
	 * the fast-drain reminder), so a device that goes quiet on the charger reminds late rather than on the dot.
	 *
	 * @param context Application context
	 *
	 * @return the configured reminder gap in milliseconds
	 */
	public static long unplugReminderGapMs(Context context) {
		return clampMinutesToMs(
				prefs(context).getInt(context.getString(R.string._pref_key_unplug_reminder_minutes), DEFAULT_UNPLUG_REMINDER_MINUTES),
				MIN_UNPLUG_REMINDER_MINUTES,
				MAX_UNPLUG_REMINDER_MINUTES);
	}

	/**
	 * Clamps a stored minutes preference to its slider range and converts it to milliseconds. The sliders constrain UI input, but a corrupt or out-of-range
	 * stored value (a 0-minute gap, say) would otherwise defeat the very interval it configures. Shared by every timing preference — the fast-drain window and
	 * reminder (#109) and the unplug reminder (#264) — so "the slider range is also the enforced range" is stated once. Pure so it is unit-testable.
	 *
	 * @param storedMinutes the raw persisted value in minutes
	 * @param minMinutes    the slider's minimum
	 * @param maxMinutes    the slider's maximum
	 *
	 * @return the clamped duration in milliseconds
	 */
	public static long clampMinutesToMs(int storedMinutes, int minMinutes, int maxMinutes) {
		return Math.max(minMinutes, Math.min(maxMinutes, storedMinutes)) * MS_PER_MINUTE;
	}

	/**
	 * How long the drain rate must hold at/above the limit before the fast-drain alert fires (#109), in milliseconds. Reads
	 * {@link #DEFAULT_FAST_DRAIN_SUSTAINED_MINUTES} when unset and always clamps, so a corrupt preference (a 0-minute window, say) can't turn a sustained-drain
	 * warning into a spike alarm.
	 *
	 * @param context Application context
	 *
	 * @return the configured window in milliseconds
	 */
	public static long fastDrainSustainedMs(Context context) {
		return clampMinutesToMs(
				prefs(context).getInt(context.getString(R.string._pref_key_fast_drain_sustained_minutes), DEFAULT_FAST_DRAIN_SUSTAINED_MINUTES),
				MIN_FAST_DRAIN_SUSTAINED_MINUTES,
				MAX_FAST_DRAIN_SUSTAINED_MINUTES);
	}

	/**
	 * The minimum gap between fast-drain reminders while the screen is off or locked (#109), in milliseconds. Reads
	 * {@link #DEFAULT_FAST_DRAIN_REMINDER_MINUTES} when unset and always clamps, for the same reason as every other timing preference here.
	 *
	 * @param context Application context
	 *
	 * @return the configured reminder gap in milliseconds
	 */
	public static long fastDrainReminderGapMs(Context context) {
		return clampMinutesToMs(
				prefs(context).getInt(context.getString(R.string._pref_key_fast_drain_reminder_minutes), DEFAULT_FAST_DRAIN_REMINDER_MINUTES),
				MIN_FAST_DRAIN_REMINDER_MINUTES,
				MAX_FAST_DRAIN_REMINDER_MINUTES);
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

	/**
	 * Whether Android refused to promote {@code PowerConnectionService} to the foreground and the user has not been told about it yet (#302).
	 * <p>
	 * Set by the service when the promotion is refused — monitoring is down from that moment — and cleared by {@code MainActivity} at the next launch, once
	 * there is a screen to explain it on. A flag rather than a count: a device whose process is killed several times a day still explains itself once, at the
	 * launch after the first kill, instead of once per kill.
	 *
	 * @param context Application context
	 *
	 * @return true when an interruption is still waiting to be reported
	 */
	public static boolean monitoringStopped(Context context) {
		return prefs(context).getBoolean(context.getString(R.string._pref_key_monitoring_stopped), false);
	}

	/**
	 * Record — or clear — the pending "the system stopped background monitoring" report (#302).
	 * <p>
	 * Written with {@code apply()} like every other write here, even though the service that sets it calls {@code stopSelf()} on the next line: the framework
	 * flushes pending {@code apply()} work before it hands a service its stop, so the flag still reaches disk if the process then goes away.
	 *
	 * @param context Application context
	 * @param stopped true to record an interruption, false once it has been reported
	 */
	public static void setMonitoringStopped(Context context, boolean stopped) {
		prefs(context).edit().putBoolean(context.getString(R.string._pref_key_monitoring_stopped), stopped).apply();
	}

	/**
	 * The user's theme choice as an {@link AppCompatDelegate} night mode, defaulting to following the system.
	 *
	 * @param context Application context
	 *
	 * @return one of the {@code AppCompatDelegate.MODE_NIGHT_*} constants
	 */
	public static int themeMode(Context context) {
		return themeModeOf(prefs(context).getString(context.getString(R.string._pref_key_theme), THEME_SYSTEM));
	}

	/**
	 * Map a stored theme value to its night mode. Anything unrecognised — an older build's value, or a corrupt one — follows the system rather than forcing
	 * a theme the user never picked.
	 *
	 * @param stored the persisted value, or null when unset
	 *
	 * @return one of the {@code AppCompatDelegate.MODE_NIGHT_*} constants
	 */
	public static int themeModeOf(String stored) {
		return switch (isNull(stored) ? THEME_SYSTEM : stored) {
			case THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
			case THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES;
			default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
		};
	}

	/**
	 * The stored theme choice as written, rather than resolved to a night mode. The gauge toggle (#333) needs the raw value: {@link #THEME_SYSTEM} is the one
	 * state a tap has to announce leaving, and {@link #themeMode} has already flattened it into a mode by the time it returns.
	 *
	 * @param context Application context
	 *
	 * @return one of {@link #THEME_SYSTEM}, {@link #THEME_LIGHT} or {@link #THEME_DARK}
	 */
	public static String themeChoice(Context context) {
		return prefs(context).getString(context.getString(R.string._pref_key_theme), THEME_SYSTEM);
	}

	/**
	 * Persist a theme choice. Writes the same key the Settings picker owns, which is what keeps the gauge toggle and the picker in step without either
	 * knowing about the other — the {@code ListPreference} is persistent and re-reads the value whenever its screen opens.
	 * <p>
	 * Applying the choice is the caller's job: {@code AppCompatDelegate.setDefaultNightMode(themeModeOf(choice))} recreates the visible activity, so it wants
	 * to happen once, after everything else this tap needs to record.
	 *
	 * @param context Application context
	 * @param choice  one of {@link #THEME_SYSTEM}, {@link #THEME_LIGHT} or {@link #THEME_DARK}
	 */
	public static void setThemeChoice(Context context, String choice) {
		prefs(context).edit().putString(context.getString(R.string._pref_key_theme), choice).apply();
	}

	/**
	 * The explicit choice a gauge tap makes, given what is on screen at the time (#333).
	 * <p>
	 * Keyed off the rendered appearance rather than the stored value, because on {@link #THEME_SYSTEM} the stored value describes the rule and says nothing
	 * about the result. The caller reads that appearance from the *activity* configuration — AppCompat applies the night mode when it themes the activity,
	 * so an application context can still be reporting the un-nighted one.
	 *
	 * @param showingDark whether the app is currently rendering dark
	 *
	 * @return {@link #THEME_LIGHT} when dark is showing, {@link #THEME_DARK} otherwise
	 */
	public static String themeChoiceOpposite(boolean showingDark) {
		return showingDark ? THEME_LIGHT : THEME_DARK;
	}

	/**
	 * Whether a gauge tap has taken the user off {@link #THEME_SYSTEM} without the offer to go back having been shown yet (#333).
	 * <p>
	 * A flag rather than a direct call because {@code setDefaultNightMode} recreates the activity: a snackbar raised at tap time dies with the instance that
	 * raised it, so the new one has to pick the message up. Same shape as the pending {@code monitoringStopped} report (#302).
	 *
	 * @param context Application context
	 *
	 * @return true when the offer is still owed
	 */
	public static boolean themeLeftSystem(Context context) {
		return prefs(context).getBoolean(context.getString(R.string._pref_key_theme_left_system), false);
	}

	/**
	 * Record — or clear — the pending "you are no longer following the system" offer (#333).
	 *
	 * @param context Application context
	 * @param pending true when a tap has just left {@link #THEME_SYSTEM}, false once the offer has been shown
	 */
	public static void setThemeLeftSystem(Context context, boolean pending) {
		prefs(context).edit().putBoolean(context.getString(R.string._pref_key_theme_left_system), pending).apply();
	}

	private static SharedPreferences prefs(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context);
	}
}
