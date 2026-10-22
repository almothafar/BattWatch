package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.BatteryDO;

import static com.almothafar.simplebatterynotifier.service.BatteryRateTracker.MAX_PLAUSIBLE_RATE_PPH;
import static com.almothafar.simplebatterynotifier.service.BatteryRateTracker.MIN_CURRENT_SAMPLES;
import static com.almothafar.simplebatterynotifier.service.BatteryRateTracker.MIN_DISPLAY_CURRENT_MA;
import static com.almothafar.simplebatterynotifier.service.BatteryRateTracker.MIN_SAMPLE_SPACING_MS;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * The drain since the last unplug, split by whether the screen was on or off at the time (#193): the <b>screen-on drain</b> and the <b>screen-off
 * drain</b>, each as an average current and a %/h. The split tells usage apart from idle drain, which the single drain rate blends together.
 * <p>
 * <b>Why a session, not the rate window.</b> {@link BatteryRateTracker}'s window covers ten minutes, which almost always holds one screen state only. This
 * accumulates from the moment the charger comes out until it goes back in — hours of both states — so each figure rests on a long horizon. The window is
 * left as it is: it still answers "right now".
 * <p>
 * <b>How time is attributed.</b> The session keeps one open interval, anchored at the last moment the screen state was observed to change, and a tally per
 * state. Every observation — a battery sample, or the screen broadcasts the monitoring service registers for — reads the screen state: if it differs from
 * the anchor's, the interval closes into the anchor state's tally and a new one opens. The screen broadcasts are what make this exact. Battery samples can
 * be tens of minutes apart in doze, but the broadcasts arrive at the toggle itself, so every interval holds a single state by construction instead of by
 * guesswork. A sample that sees the change first, because its broadcast is still in flight, closes the interval a moment early, which costs nothing.
 * <p>
 * <b>What the session cannot vouch for.</b> While monitoring is down, no broadcast arrives and toggles go unseen, so the interval open at that point may
 * span both states. {@link #onMonitoringStarted} therefore drops it rather than guess. Plugging in ends the session; the next unplug starts a fresh one.
 * <p>
 * <b>Which figure comes from where.</b> The %/h is the level drop over the time spent in that state, not current &divide; capacity: screen-off sampling is
 * sparse by nature, so the long horizon is what makes it trustworthy, and the level is the reading that survives where the charge counter doesn't (#69 /
 * #94). The current is the average of the readings taken in that state. Each state shows nothing until it has run for its own warm-up, so one can be a
 * real figure while the other is still on its way.
 * <p>
 * State lives in the backup-excluded transient file ({@link TransientState}), like the rate window.
 */
public final class ScreenDrainTracker {

	// Persisted session: whether one is open, the open interval's anchor, the last current reading taken, and one tally per state (suffix _on / _off).
	private static final String PREF_ACTIVE = "_screen_drain_active";
	private static final String PREF_ANCHOR_MS = "_screen_drain_anchor_ms";
	private static final String PREF_ANCHOR_LEVEL = "_screen_drain_anchor_level";
	private static final String PREF_ANCHOR_SCREEN_ON = "_screen_drain_anchor_screen_on";
	private static final String PREF_LAST_CURRENT_MS = "_screen_drain_last_current_ms";
	private static final String PREF_MS = "_screen_drain_ms";
	private static final String PREF_LEVEL_DROP = "_screen_drain_level_drop";
	private static final String PREF_CURRENT_SUM = "_screen_drain_current_sum";
	private static final String PREF_CURRENT_COUNT = "_screen_drain_current_count";
	private static final String SUFFIX_ON = "_on";
	private static final String SUFFIX_OFF = "_off";

	// Per-state warm-up: long enough for the level to move a few times at that state's typical drain. Screen-on drain runs near 10 %/h, a tick every
	// six minutes; screen-off drain near 1 %/h, a tick an hour.
	static final long SCREEN_ON_MIN_MS = 15L * 60 * 1000;
	static final long SCREEN_OFF_MIN_MS = 60L * 60 * 1000;

	private static final double TENTHS_PER_HOUR = 10 * 3_600_000.0;

	private ScreenDrainTracker() {
		// Utility class - prevent instantiation
	}

	/**
	 * Folds one observation into the session and returns the split as it now stands. Fed by {@link BatteryRateTracker#record}, so the battery broadcasts,
	 * the screen broadcasts and the details table's refresh all reach it.
	 *
	 * @param prefs     the transient preferences
	 * @param now       current time in millis
	 * @param batteryDO the reading taken with this observation
	 * @param screenOn  whether the screen is on right now
	 *
	 * @return the split, or {@link ScreenSplit#NONE} while plugged in or when the reading has no usable level
	 */
	static ScreenSplit record(SharedPreferences prefs, long now, BatteryDO batteryDO, boolean screenOn) {
		if (!BatteryRateTracker.hasUsableLevel(batteryDO)) {
			return read(prefs, now, batteryDO);
		}
		final Session previous = loadSession(prefs);
		final Session updated = fold(previous, now, batteryDO.getBatteryPercentageInt(), batteryDO.getCurrentMicroAmps(), screenOn, isPlugged(batteryDO));
		// Persist only on change: most battery broadcasts neither toggle the screen nor clear the current-sample spacing.
		if (!updated.equals(previous)) {
			saveSession(prefs, updated);
		}
		return summarize(updated, now, batteryDO.getBatteryPercentageInt());
	}

	/**
	 * Reads the split without adding an observation. The open interval counts towards the state it was opened in.
	 *
	 * @param prefs     the transient preferences
	 * @param now       current time in millis
	 * @param batteryDO the current reading
	 *
	 * @return the split, or {@link ScreenSplit#NONE} while plugged in or when the reading has no usable level
	 */
	static ScreenSplit read(SharedPreferences prefs, long now, BatteryDO batteryDO) {
		if (!BatteryRateTracker.hasUsableLevel(batteryDO) || isPlugged(batteryDO)) {
			return ScreenSplit.NONE;
		}
		return summarize(loadSession(prefs), now, batteryDO.getBatteryPercentageInt());
	}

	/**
	 * Called when monitoring starts — at boot, and whenever the system recreates the service after killing it. The interval open at that point ran while
	 * nothing was watching the screen, so it is dropped instead of being credited to the state it began in; the tallies are kept.
	 *
	 * @param context   Application context
	 * @param batteryDO the reading taken as monitoring starts, or null when there is none
	 */
	public static void onMonitoringStarted(Context context, BatteryDO batteryDO) {
		if (isNull(batteryDO)) {
			return;
		}
		restart(TransientState.prefs(context), System.currentTimeMillis(), batteryDO, SystemService.isScreenOn(context));
	}

	/**
	 * {@link #onMonitoringStarted} with the clock and the screen state passed in.
	 *
	 * @param prefs     the transient preferences
	 * @param now       current time in millis
	 * @param batteryDO the reading taken as monitoring starts
	 * @param screenOn  whether the screen is on right now
	 */
	static void restart(SharedPreferences prefs, long now, BatteryDO batteryDO, boolean screenOn) {
		// Without a level there is nothing to anchor a new interval at; the next usable reading's fold starts one if needed.
		if (!BatteryRateTracker.hasUsableLevel(batteryDO)) {
			return;
		}
		final int level = batteryDO.getBatteryPercentageInt();
		final Session previous = loadSession(prefs);
		final Session updated;
		if (isPlugged(batteryDO)) {
			updated = Session.INACTIVE;
		} else if (previous.active()) {
			updated = previous.anchoredAt(now, level, screenOn);
		} else {
			updated = Session.startedAt(now, level, screenOn);
		}
		if (!updated.equals(previous)) {
			saveSession(prefs, updated);
		}
	}

	/**
	 * Formats one state's drain for the details table and the notification's pull-down: {@code "−392 mA · 10.4%/h"}, or whichever half is ready, with
	 * Western digits in every locale (#96). The %/h carries one decimal, unlike the 10-minute drain rate's whole number: screen-off drain mostly sits
	 * between 0.3 and 2 %/h, where whole numbers would read 0 or 1, and the hours-long horizon is what earns the extra digit.
	 *
	 * @param context Application context
	 * @param drain   one state's drain
	 *
	 * @return the value, or null while neither figure is ready
	 */
	public static String formatValue(Context context, ScreenDrain drain) {
		final String current = drain.hasCurrent() ? BatteryRateTracker.formatCurrentValue(context, drain.avgCurrentMilliAmps()) : null;
		final String rate = drain.hasRate() ? formatTenthsRate(context, drain.tenthsPercentPerHour()) : null;
		if (nonNull(current) && nonNull(rate)) {
			return current + OngoingStatusContent.DETAIL_SEPARATOR + rate;
		}
		return nonNull(current) ? current : rate;
	}

	private static String formatTenthsRate(Context context, int tenths) {
		return context.getString(R.string.battery_rate_value, (tenths / 10) + "." + (tenths % 10));
	}

	/**
	 * Folds one observation into a session. Pure so the attribution rules are testable without the clock.
	 *
	 * @param session          the session so far
	 * @param now              current time in millis
	 * @param level            the battery level in percent
	 * @param currentMicroAmps the instantaneous current in µA, or {@link Integer#MIN_VALUE} when unsupported
	 * @param screenOn         whether the screen is on right now
	 * @param plugged          whether a charger is connected
	 *
	 * @return the updated session, value-equal to {@code session} when nothing changed
	 */
	static Session fold(Session session, long now, int level, int currentMicroAmps, boolean screenOn, boolean plugged) {
		if (plugged) {
			return Session.INACTIVE;
		}
		final Session observed = observeScreen(session, now, level, screenOn);
		return withCurrentSample(observed, now, currentMicroAmps, screenOn);
	}

	private static Session observeScreen(Session session, long now, int level, boolean screenOn) {
		if (!session.active()) {
			return Session.startedAt(now, level, screenOn);
		}
		// The clock was set back: the open interval can't be measured, so it is dropped rather than left to swallow the time until the clock catches up.
		if (now < session.anchorMillis()) {
			return session.anchoredAt(now, level, screenOn);
		}
		if (screenOn == session.anchorScreenOn()) {
			return session;
		}
		return session.closedAt(now, level).anchoredAt(now, level, screenOn);
	}

	/** Adds the reading to the current state's tally, under the rate window's spacing rule so the details table's 3 s refresh can't outweigh doze. */
	private static Session withCurrentSample(Session session, long now, int currentMicroAmps, boolean screenOn) {
		if (!BatteryRateTracker.isPlausibleCurrentMicroAmps(currentMicroAmps) || now - session.lastCurrentMillis() < MIN_SAMPLE_SPACING_MS) {
			return session;
		}
		return session.withCurrent(now, currentMicroAmps, screenOn);
	}

	/**
	 * The split a session amounts to at {@code now}, with the open interval closed into its state. Pure so the gates are testable.
	 *
	 * @param session the session
	 * @param now     current time in millis
	 * @param level   the battery level in percent
	 *
	 * @return the split, or {@link ScreenSplit#NONE} when no session is open
	 */
	static ScreenSplit summarize(Session session, long now, int level) {
		if (!session.active()) {
			return ScreenSplit.NONE;
		}
		final Session closed = session.closedAt(now, level);
		return new ScreenSplit(drainOf(closed.on(), SCREEN_ON_MIN_MS), drainOf(closed.off(), SCREEN_OFF_MIN_MS));
	}

	private static ScreenDrain drainOf(Tally tally, long minMillis) {
		if (tally.millis() < minMillis) {
			return ScreenDrain.NONE;
		}
		final int tenths = (int) Math.round(tally.levelDrop() * TENTHS_PER_HOUR / tally.millis());
		final boolean hasRate = tenths >= 1 && tenths <= MAX_PLAUSIBLE_RATE_PPH * 10;
		// The same floor as the window's average (#152): below it the readings are deep-sleep noise or a wrong-unit misread.
		final int avgMicroAmps = tally.currentSamples() > 0 ? (int) Math.round((double) tally.currentSumMicroAmps() / tally.currentSamples()) : 0;
		final boolean hasCurrent = tally.currentSamples() >= MIN_CURRENT_SAMPLES && Math.round(Math.abs(avgMicroAmps) / 1000f) >= MIN_DISPLAY_CURRENT_MA;
		return new ScreenDrain(
				hasCurrent,
				hasCurrent ? BatteryRateTracker.signedCurrentMilliAmps(avgMicroAmps, false) : 0,
				hasRate,
				hasRate ? tenths : 0);
	}

	private static boolean isPlugged(BatteryDO batteryDO) {
		return batteryDO.getPlugged() > 0;
	}

	static Session loadSession(SharedPreferences prefs) {
		if (!prefs.getBoolean(PREF_ACTIVE, false)) {
			return Session.INACTIVE;
		}
		return new Session(
				true,
				prefs.getLong(PREF_ANCHOR_MS, 0),
				prefs.getInt(PREF_ANCHOR_LEVEL, 0),
				prefs.getBoolean(PREF_ANCHOR_SCREEN_ON, false),
				prefs.getLong(PREF_LAST_CURRENT_MS, 0),
				loadTally(prefs, SUFFIX_ON),
				loadTally(prefs, SUFFIX_OFF));
	}

	private static Tally loadTally(SharedPreferences prefs, String suffix) {
		return new Tally(
				prefs.getLong(PREF_MS + suffix, 0),
				prefs.getInt(PREF_LEVEL_DROP + suffix, 0),
				prefs.getLong(PREF_CURRENT_SUM + suffix, 0),
				prefs.getInt(PREF_CURRENT_COUNT + suffix, 0));
	}

	private static void saveSession(SharedPreferences prefs, Session session) {
		final SharedPreferences.Editor editor = prefs.edit()
		                                             .putBoolean(PREF_ACTIVE, session.active())
		                                             .putLong(PREF_ANCHOR_MS, session.anchorMillis())
		                                             .putInt(PREF_ANCHOR_LEVEL, session.anchorLevel())
		                                             .putBoolean(PREF_ANCHOR_SCREEN_ON, session.anchorScreenOn())
		                                             .putLong(PREF_LAST_CURRENT_MS, session.lastCurrentMillis());
		saveTally(editor, SUFFIX_ON, session.on());
		saveTally(editor, SUFFIX_OFF, session.off());
		editor.apply();
	}

	private static void saveTally(SharedPreferences.Editor editor, String suffix, Tally tally) {
		editor.putLong(PREF_MS + suffix, tally.millis())
		      .putInt(PREF_LEVEL_DROP + suffix, tally.levelDrop())
		      .putLong(PREF_CURRENT_SUM + suffix, tally.currentSumMicroAmps())
		      .putInt(PREF_CURRENT_COUNT + suffix, tally.currentSamples());
	}

	/**
	 * One state's running totals.
	 *
	 * @param millis              time spent in the state
	 * @param levelDrop           level points lost in the state (negative only if the level rose)
	 * @param currentSumMicroAmps sum of the current readings taken in the state, in µA
	 * @param currentSamples      how many readings that sum holds
	 */
	record Tally(long millis, int levelDrop, long currentSumMicroAmps, int currentSamples) {
		static final Tally EMPTY = new Tally(0, 0, 0, 0);

		Tally plusInterval(long intervalMillis, int intervalDrop) {
			return new Tally(millis + intervalMillis, levelDrop + intervalDrop, currentSumMicroAmps, currentSamples);
		}

		Tally plusCurrent(int microAmps) {
			return new Tally(millis, levelDrop, currentSumMicroAmps + microAmps, currentSamples + 1);
		}
	}

	/**
	 * The session since the last unplug.
	 *
	 * @param active            whether a session is open (false while plugged in, and before the first unplugged observation)
	 * @param anchorMillis      when the open interval began
	 * @param anchorLevel       the level when it began
	 * @param anchorScreenOn    the screen state it is being spent in
	 * @param lastCurrentMillis when the last current reading was taken into a tally, for the spacing rule
	 * @param on                the screen-on tally
	 * @param off               the screen-off tally
	 */
	record Session(boolean active,
	               long anchorMillis,
	               int anchorLevel,
	               boolean anchorScreenOn,
	               long lastCurrentMillis,
	               Tally on,
	               Tally off) {

		static final Session INACTIVE = new Session(false, 0, 0, false, 0, Tally.EMPTY, Tally.EMPTY);

		static Session startedAt(long now, int level, boolean screenOn) {
			return new Session(true, now, level, screenOn, 0, Tally.EMPTY, Tally.EMPTY);
		}

		Session anchoredAt(long now, int level, boolean screenOn) {
			return new Session(true, now, level, screenOn, lastCurrentMillis, on, off);
		}

		Session withCurrent(long now, int microAmps, boolean screenOn) {
			return screenOn
			       ? new Session(true, anchorMillis, anchorLevel, anchorScreenOn, now, on.plusCurrent(microAmps), off)
			       : new Session(true, anchorMillis, anchorLevel, anchorScreenOn, now, on, off.plusCurrent(microAmps));
		}

		/**
		 * Closes the open interval into the tally of the state it was spent in; the anchor is left for the caller to move. An interval that would end before
		 * it began (the clock was set back) credits nothing, so its level drop can't land on zero time.
		 */
		Session closedAt(long now, int level) {
			if (now < anchorMillis) {
				return this;
			}
			final long interval = now - anchorMillis;
			final int drop = anchorLevel - level;
			return anchorScreenOn
			       ? new Session(true, anchorMillis, anchorLevel, true, lastCurrentMillis, on.plusInterval(interval, drop), off)
			       : new Session(true, anchorMillis, anchorLevel, false, lastCurrentMillis, on, off.plusInterval(interval, drop));
		}
	}

	/**
	 * One screen state's drain since the last unplug, each figure flagged by whether it is ready to show.
	 *
	 * @param hasCurrent           whether an average current is available
	 * @param avgCurrentMilliAmps  the average current in mA, negative (valid only when {@code hasCurrent})
	 * @param hasRate              whether a %/h is available
	 * @param tenthsPercentPerHour the drain in tenths of a %/h, so 15 is 1.5 %/h (valid only when {@code hasRate})
	 */
	public record ScreenDrain(boolean hasCurrent, int avgCurrentMilliAmps, boolean hasRate, int tenthsPercentPerHour) {
		static final ScreenDrain NONE = new ScreenDrain(false, 0, false, 0);
	}

	/**
	 * The screen-on and screen-off drain, side by side.
	 *
	 * @param on  the screen-on drain
	 * @param off the screen-off drain
	 */
	public record ScreenSplit(ScreenDrain on, ScreenDrain off) {
		public static final ScreenSplit NONE = new ScreenSplit(ScreenDrain.NONE, ScreenDrain.NONE);
	}
}
