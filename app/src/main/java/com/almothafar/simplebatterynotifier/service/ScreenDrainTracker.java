package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.model.PowerSource;

import java.util.ArrayList;
import java.util.List;

import static com.almothafar.simplebatterynotifier.service.BatteryRateTracker.MIN_CURRENT_SAMPLES;
import static com.almothafar.simplebatterynotifier.service.BatteryRateTracker.MIN_SAMPLE_SPACING_MS;
import static com.almothafar.simplebatterynotifier.service.BatteryRateTracker.PROPERTY_UNSUPPORTED;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * The drain since the last unplug, split by whether the screen was on or off at the time (#193): the <b>screen-on drain</b> and the <b>screen-off
 * drain</b>, each as an average current and a %/h. The split tells usage apart from idle drain, which the single drain rate blends together. The two
 * tallies together give the <b>overall drain</b> over the whole time off the charger, which the time remaining projects from (#354).
 * <p>
 * <b>Why a session, not the rate window.</b> {@link BatteryRateTracker}'s window covers ten minutes, which almost always holds one screen state only. This
 * accumulates from the moment the charger comes out until it goes back in — hours of both states — so each figure rests on a long horizon. The window is
 * left as it is: it still answers "right now".
 * <p>
 * <b>How time is attributed.</b> The session keeps one open stretch, anchored at the last observation that moved it, and a tally per state. Every
 * observation carries the screen state: in the other state than the anchor's, the stretch closes into the anchor state's tally and a new one opens; in the
 * same state, it closes and reopens at once, which saves the progress. The screen broadcasts are what make this exact. Battery samples can be tens of
 * minutes apart in doze, but the broadcasts arrive at the toggle itself, so every stretch holds a single state by construction instead of by guesswork. A
 * sample that sees the change first, because its broadcast is still in flight, closes the stretch a moment early, which costs nothing.
 * <p>
 * <b>Only what monitoring watched.</b> Only the monitoring service's own receivers observe — {@link #onBatteryChanged} and {@link #onScreenChanged} — so a
 * figure never rests on time when nothing was listening for toggles; the details table only reads. What is shown is the closed tallies, and the open
 * stretch counts once the next observation closes it. When monitoring starts again after a gap, {@link #onMonitoringStarted} drops the stretch that was
 * open, which by then is the gap plus at most the last stretch between observations. A charge can go unseen the same way, so a level that has risen
 * since the anchor starts a new session. Plugging in ends the session; the next unplug starts a fresh one.
 * <p>
 * <b>Which figure comes from where.</b> The %/h is the level drop over the time spent in that state, not current &divide; capacity: screen-off sampling is
 * sparse by nature, so the long horizon is what makes it trustworthy, and the level is the reading that survives where the charge counter doesn't (#69 /
 * #94). It needs no plausibility ceiling: past the warm-up a level can't fall faster than 100 points in a quarter of an hour. The current is the average of
 * the readings taken in that state, except around a transition — the unplug or a toggle, and the rate window's spacing after it — where the display is
 * still powering down or waking, or the charger's current still shows. Each state shows nothing until it has run for its own warm-up, so one can be a
 * real figure while the other is still on its way.
 * <p>
 * State lives in the backup-excluded transient file ({@link TransientState}), like the rate window.
 */
public final class ScreenDrainTracker {

	// Persisted session: the open stretch's anchor (absent while no session is open), the spacing mark for current readings, and one tally per state,
	// keyed by the state's suffix.
	private static final String PREF_ACTIVE = "_screen_drain_active";
	private static final String PREF_ANCHOR_MS = "_screen_drain_anchor_ms";
	private static final String PREF_ANCHOR_LEVEL = "_screen_drain_anchor_level";
	private static final String PREF_ANCHOR_SCREEN_ON = "_screen_drain_anchor_screen_on";
	private static final String PREF_LAST_READING_MS = "_screen_drain_last_reading_ms";
	private static final String PREF_MS = "_screen_drain_ms";
	private static final String PREF_LEVEL_DROP = "_screen_drain_level_drop";
	private static final String PREF_CURRENT_SUM = "_screen_drain_current_sum";
	private static final String PREF_CURRENT_COUNT = "_screen_drain_current_count";

	// A level this many points above the anchor means the battery charged while nothing was watching. One point is left to the gauge's own wobble.
	static final int UNSEEN_CHARGE_POINTS = 2;

	// The overall drain waits an hour, as the screen-off drain does: by then one level tick moves it by 1 %/h at most. Until then the time remaining projects
	// from the 10-minute drain rate.
	static final long OVERALL_WARM_UP_MS = 60L * 60 * 1000;

	private ScreenDrainTracker() {
		// Utility class - prevent instantiation
	}

	/**
	 * Called on each {@code ACTION_BATTERY_CHANGED} the monitoring service receives, before the rate is read for the notification: one observation, with a
	 * current reading.
	 *
	 * @param context   Application context
	 * @param batteryDO the reading the broadcast carried
	 */
	public static void onBatteryChanged(Context context, BatteryDO batteryDO) {
		batteryChangedAt(TransientState.prefs(context), System.currentTimeMillis(), batteryDO, ScreenState.of(context));
	}

	/**
	 * Called on {@code ACTION_SCREEN_ON} and {@code ACTION_SCREEN_OFF}: closes the open stretch at the toggle itself. It leaves the 10-minute rate window
	 * alone and takes no current reading, since one taken at a toggle belongs to neither state.
	 *
	 * @param context   Application context
	 * @param batteryDO the reading taken with the broadcast, or null when there is none
	 */
	public static void onScreenChanged(Context context, BatteryDO batteryDO) {
		if (isNull(batteryDO)) {
			return;
		}
		screenChangedAt(TransientState.prefs(context), System.currentTimeMillis(), batteryDO, ScreenState.of(context));
	}

	/**
	 * Called when monitoring starts — at boot, and whenever the system recreates the service after killing it. The stretch open at that point ran into
	 * time when nothing was watching the screen, so it is dropped instead of being credited to the state it began in; the tallies are kept.
	 *
	 * @param context   Application context
	 * @param batteryDO the reading taken as monitoring starts, or null when there is none
	 */
	public static void onMonitoringStarted(Context context, BatteryDO batteryDO) {
		if (isNull(batteryDO)) {
			return;
		}
		monitoringStartedAt(TransientState.prefs(context), System.currentTimeMillis(), batteryDO, ScreenState.of(context));
	}

	/**
	 * Whether the split is being kept for this reading: only on battery. A phone on the charger drains net of what the charger feeds in, so any charger
	 * ends the session — a weak USB port included. The details table shows its screen-on and screen-off drain rows by the same test, so a phone held at a
	 * charge cap, which reports NOT_CHARGING while plugged in, shows neither rather than two rows that can never fill.
	 *
	 * @param batteryDO the current reading
	 *
	 * @return true when no charger is connected
	 */
	public static boolean appliesTo(BatteryDO batteryDO) {
		return PowerSource.fromPlugged(batteryDO.getPlugged()) == PowerSource.BATTERY;
	}

	/**
	 * Formats one state's drain for the details table and the notification's pull-down: {@code "−392 mA · 10.4%/h · ~8h 10m"}, or whichever figures are
	 * ready, with Western digits in every locale (#96). The time is how long the level would last if the phone stayed in that state (#354), so the two
	 * states bracket the time remaining. The %/h carries one decimal, unlike the 10-minute drain rate's whole number: screen-off drain mostly sits between
	 * 0.3 and 2 %/h, where whole numbers would read 0 or 1, and the hours-long horizon is what earns the extra digit.
	 * <p>
	 * All three seldom fit one line of a phone's details table, so nothing inside a figure can break: the value wraps between figures, never between "~7h"
	 * and "48m".
	 *
	 * @param context Application context
	 * @param drain   one state's drain
	 * @param level   the battery level in percent, which the time projects from
	 *
	 * @return the value, or null while neither figure is ready
	 */
	public static String formatValue(Context context, ScreenDrain drain, int level) {
		final List<String> figures = new ArrayList<>(3);
		if (drain.hasCurrent()) {
			figures.add(unbroken(BatteryRateTracker.formatCurrentValue(context, drain.avgCurrentMilliAmps())));
		}
		if (drain.hasRate()) {
			figures.add(unbroken(formatTenthsRate(context, drain.tenthsPercentPerHour())));
			final int minutes = BatteryRateTracker.estimateMinutesToEmptyFromTenths(level, drain.tenthsPercentPerHour());
			if (minutes > 0) {
				figures.add(unbroken(BatteryRateTracker.formatDuration(context, minutes)));
			}
		}
		return figures.isEmpty() ? null : String.join(OngoingStatusContent.DETAIL_SEPARATOR, figures);
	}

	/**
	 * {@link #onBatteryChanged} with the clock and the screen state passed in.
	 *
	 * @param prefs     the transient preferences
	 * @param now       current time in millis
	 * @param batteryDO the reading the broadcast carried
	 * @param screen    the screen state right now
	 */
	static void batteryChangedAt(SharedPreferences prefs, long now, BatteryDO batteryDO, ScreenState screen) {
		if (BatteryRateTracker.hasUsableLevel(batteryDO)) {
			update(prefs, new Moment(now, batteryDO.getBatteryPercentageInt(), screen), batteryDO.getCurrentMicroAmps(), appliesTo(batteryDO));
		}
	}

	/**
	 * {@link #onScreenChanged} with the clock and the screen state passed in.
	 *
	 * @param prefs     the transient preferences
	 * @param now       current time in millis
	 * @param batteryDO the reading taken with the broadcast
	 * @param screen    the screen state right now
	 */
	static void screenChangedAt(SharedPreferences prefs, long now, BatteryDO batteryDO, ScreenState screen) {
		// PROPERTY_UNSUPPORTED is "no reading": every current check already skips it.
		if (BatteryRateTracker.hasUsableLevel(batteryDO)) {
			update(prefs, new Moment(now, batteryDO.getBatteryPercentageInt(), screen), PROPERTY_UNSUPPORTED, appliesTo(batteryDO));
		}
	}

	/**
	 * {@link #onMonitoringStarted} with the clock and the screen state passed in.
	 *
	 * @param prefs     the transient preferences
	 * @param now       current time in millis
	 * @param batteryDO the reading taken as monitoring starts
	 * @param screen    the screen state right now
	 */
	static void monitoringStartedAt(SharedPreferences prefs, long now, BatteryDO batteryDO, ScreenState screen) {
		// Without a level there is nothing to anchor a new stretch at; the next usable observation does it.
		if (!BatteryRateTracker.hasUsableLevel(batteryDO)) {
			return;
		}
		final Session previous = loadSession(prefs);
		final Moment moment = new Moment(now, batteryDO.getBatteryPercentageInt(), screen);
		final Session updated = appliesTo(batteryDO) ? reanchored(previous, moment) : Session.INACTIVE;
		if (!updated.equals(previous)) {
			saveSession(prefs, updated);
		}
	}

	/**
	 * Reads the split from the closed tallies, without observing anything.
	 *
	 * @param prefs     the transient preferences
	 * @param batteryDO the current reading
	 *
	 * @return the split, or {@link ScreenSplit#NONE} while plugged in or when the reading has no usable level
	 */
	static ScreenSplit read(SharedPreferences prefs, BatteryDO batteryDO) {
		if (!BatteryRateTracker.hasUsableLevel(batteryDO) || !appliesTo(batteryDO)) {
			return ScreenSplit.NONE;
		}
		return summarize(loadSession(prefs));
	}

	/**
	 * Folds one observation into a session. Pure so the attribution rules are testable without the clock.
	 *
	 * @param session          the session so far
	 * @param moment           when the observation was made, at what level, in which screen state
	 * @param currentMicroAmps the instantaneous current in µA, or {@link BatteryRateTracker#PROPERTY_UNSUPPORTED} for no reading
	 * @param onBattery        whether no charger is connected
	 *
	 * @return the updated session, value-equal to {@code session} when nothing changed
	 */
	static Session fold(Session session, Moment moment, int currentMicroAmps, boolean onBattery) {
		if (!onBattery) {
			return Session.INACTIVE;
		}
		final boolean toggled = session.active() && moment.screen() != session.anchor().screen();
		final Session observed = observe(session, moment);
		// A toggle counts as a reading for the spacing rule, so neither it nor the readings just after it land in a tally; a new session starts that way.
		return toggled ? observed.readingAt(moment.millis()) : withCurrentSample(observed, moment, currentMicroAmps);
	}

	/**
	 * The split the session's closed tallies amount to. Pure so the gates are testable.
	 *
	 * @param session the session
	 *
	 * @return the split, or {@link ScreenSplit#NONE} when no session is open
	 */
	static ScreenSplit summarize(Session session) {
		if (!session.active()) {
			return ScreenSplit.NONE;
		}
		return new ScreenSplit(
				session.onTally().toDrain(ScreenState.ON.warmUpMillis),
				session.offTally().toDrain(ScreenState.OFF.warmUpMillis),
				session.onTally().plus(session.offTally()).toDrain(OVERALL_WARM_UP_MS));
	}

	static Session loadSession(SharedPreferences prefs) {
		if (!prefs.getBoolean(PREF_ACTIVE, false)) {
			return Session.INACTIVE;
		}
		final Moment anchor = new Moment(
				prefs.getLong(PREF_ANCHOR_MS, 0),
				prefs.getInt(PREF_ANCHOR_LEVEL, 0),
				ScreenState.of(prefs.getBoolean(PREF_ANCHOR_SCREEN_ON, false)));
		return new Session(anchor, prefs.getLong(PREF_LAST_READING_MS, 0), loadTally(prefs, ScreenState.ON), loadTally(prefs, ScreenState.OFF));
	}

	private static void update(SharedPreferences prefs, Moment moment, int currentMicroAmps, boolean onBattery) {
		final Session previous = loadSession(prefs);
		final Session updated = fold(previous, moment, currentMicroAmps, onBattery);
		// Persist only on change: a broadcast inside the spacing of the last observation moves nothing.
		if (!updated.equals(previous)) {
			saveSession(prefs, updated);
		}
	}

	private static Session observe(Session session, Moment moment) {
		// A clock set back leaves the open stretch unmeasurable, so it is dropped rather than left to swallow the time until the clock catches up.
		if (!session.active() || chargedUnseen(session, moment) || moment.millis() < session.anchor().millis()) {
			return reanchored(session, moment);
		}
		// In the same state, close and reopen at most once per spacing: often enough that a restart loses little, rarely enough not to rewrite the file on
		// every broadcast.
		if (moment.screen() == session.anchor().screen() && moment.millis() - session.anchor().millis() < MIN_SAMPLE_SPACING_MS) {
			return session;
		}
		return session.closedAt(moment).anchoredAt(moment);
	}

	/** Opens a new stretch at the moment without crediting the old one; a session that isn't open, or that missed a charge, starts over instead. */
	private static Session reanchored(Session session, Moment moment) {
		if (!session.active() || chargedUnseen(session, moment)) {
			return Session.startedAt(moment);
		}
		return session.anchoredAt(moment);
	}

	private static boolean chargedUnseen(Session session, Moment moment) {
		return moment.level() >= session.anchor().level() + UNSEEN_CHARGE_POINTS;
	}

	/**
	 * Adds the reading to the current state's tally, under the rate window's spacing rule so the details table's refresh and bursts of broadcasts can't
	 * outweigh doze. A reading stamped before the last one (the clock was set back) counts, rather than waiting for the clock to catch up.
	 */
	private static Session withCurrentSample(Session session, Moment moment, int currentMicroAmps) {
		final long sinceLast = moment.millis() - session.lastReadingMillis();
		if (!BatteryRateTracker.isPlausibleCurrentMicroAmps(currentMicroAmps) || (sinceLast >= 0 && sinceLast < MIN_SAMPLE_SPACING_MS)) {
			return session;
		}
		return session.withTally(moment.screen(), session.tally(moment.screen()).plusCurrent(currentMicroAmps)).readingAt(moment.millis());
	}

	/** The figure with nowhere for a line to break inside it: its spaces become no-break spaces, and a word joiner holds the "h" of "%/h" to its slash. */
	private static String unbroken(String figure) {
		return figure.replace(' ', '\u00A0').replace("/", "/\u2060");
	}

	private static String formatTenthsRate(Context context, int tenths) {
		return context.getString(R.string.battery_rate_value, (tenths / 10) + "." + (tenths % 10));
	}

	private static Tally loadTally(SharedPreferences prefs, ScreenState screen) {
		return new Tally(
				prefs.getLong(PREF_MS + screen.prefSuffix, 0),
				prefs.getInt(PREF_LEVEL_DROP + screen.prefSuffix, 0),
				prefs.getLong(PREF_CURRENT_SUM + screen.prefSuffix, 0),
				prefs.getInt(PREF_CURRENT_COUNT + screen.prefSuffix, 0));
	}

	private static void saveSession(SharedPreferences prefs, Session session) {
		final SharedPreferences.Editor editor = prefs.edit().putBoolean(PREF_ACTIVE, session.active());
		if (session.active()) {
			editor.putLong(PREF_ANCHOR_MS, session.anchor().millis())
			      .putInt(PREF_ANCHOR_LEVEL, session.anchor().level())
			      .putBoolean(PREF_ANCHOR_SCREEN_ON, session.anchor().screen() == ScreenState.ON);
		}
		editor.putLong(PREF_LAST_READING_MS, session.lastReadingMillis());
		saveTally(editor, ScreenState.ON, session.onTally());
		saveTally(editor, ScreenState.OFF, session.offTally());
		editor.apply();
	}

	private static void saveTally(SharedPreferences.Editor editor, ScreenState screen, Tally tally) {
		editor.putLong(PREF_MS + screen.prefSuffix, tally.millis())
		      .putInt(PREF_LEVEL_DROP + screen.prefSuffix, tally.levelDrop())
		      .putLong(PREF_CURRENT_SUM + screen.prefSuffix, tally.currentSumMicroAmps())
		      .putInt(PREF_CURRENT_COUNT + screen.prefSuffix, tally.currentSamples());
	}

	/**
	 * The two states the split is keyed on, each with its own warm-up: long enough for the level to move a few times at that state's typical drain.
	 * Screen-on drain runs near 10 %/h, a tick every six minutes; screen-off drain near 1 %/h, a tick an hour.
	 */
	enum ScreenState {
		ON(15L * 60 * 1000, "_on"),
		OFF(60L * 60 * 1000, "_off");

		final long warmUpMillis;
		final String prefSuffix;

		ScreenState(long warmUpMillis, String prefSuffix) {
			this.warmUpMillis = warmUpMillis;
			this.prefSuffix = prefSuffix;
		}

		static ScreenState of(boolean screenOn) {
			return screenOn ? ON : OFF;
		}

		/** The screen state right now, as {@link SystemService#isScreenOn} reads it. */
		static ScreenState of(Context context) {
			return of(SystemService.isScreenOn(context));
		}
	}

	/**
	 * A point in the session: when, at what level, and in which screen state.
	 *
	 * @param millis the time in millis
	 * @param level  the battery level in percent
	 * @param screen the screen state
	 */
	record Moment(long millis, int level, ScreenState screen) {
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

		Tally plusStretch(long stretchMillis, int stretchDrop) {
			return new Tally(millis + stretchMillis, levelDrop + stretchDrop, currentSumMicroAmps, currentSamples);
		}

		Tally plusCurrent(int microAmps) {
			return new Tally(millis, levelDrop, currentSumMicroAmps + microAmps, currentSamples + 1);
		}

		/** Two tallies as one, for the overall drain. */
		Tally plus(Tally other) {
			return new Tally(
					millis + other.millis,
					levelDrop + other.levelDrop,
					currentSumMicroAmps + other.currentSumMicroAmps,
					currentSamples + other.currentSamples);
		}

		/** What the tally shows once past the state's warm-up; the window's display floor hides deep-sleep noise and misreads (#152). */
		ScreenDrain toDrain(long warmUpMillis) {
			if (millis < warmUpMillis) {
				return ScreenDrain.NONE;
			}
			final double hours = millis / 3_600_000.0;
			final int tenths = (int) Math.round(levelDrop * 10 / hours);
			final int avgMicroAmps = currentSamples > 0 ? (int) Math.round((double) currentSumMicroAmps / currentSamples) : 0;
			final boolean hasCurrent = currentSamples >= MIN_CURRENT_SAMPLES && BatteryRateTracker.clearsDisplayFloor(avgMicroAmps);
			final boolean hasRate = tenths >= 1;
			return new ScreenDrain(hasCurrent, hasCurrent ? BatteryRateTracker.signedCurrentMilliAmps(avgMicroAmps, false) : 0, hasRate, hasRate ? tenths : 0);
		}
	}

	/**
	 * The session since the last unplug.
	 *
	 * @param anchor            where the open stretch began, or null while no session is open (plugged in, or before the first observation on battery)
	 * @param lastReadingMillis when the last current reading went into a tally, or the last transition, which counts as one for the spacing rule
	 * @param onTally           the screen-on tally
	 * @param offTally          the screen-off tally
	 */
	record Session(Moment anchor, long lastReadingMillis, Tally onTally, Tally offTally) {

		static final Session INACTIVE = new Session(null, 0, Tally.EMPTY, Tally.EMPTY);

		/** A fresh session; its start counts as a reading, so the charger's current still showing at the unplug lands in no tally. */
		static Session startedAt(Moment moment) {
			return new Session(moment, moment.millis(), Tally.EMPTY, Tally.EMPTY);
		}

		boolean active() {
			return nonNull(anchor);
		}

		Tally tally(ScreenState screen) {
			return screen == ScreenState.ON ? onTally : offTally;
		}

		Session anchoredAt(Moment moment) {
			return new Session(moment, lastReadingMillis, onTally, offTally);
		}

		Session readingAt(long millis) {
			return new Session(anchor, millis, onTally, offTally);
		}

		Session withTally(ScreenState screen, Tally tally) {
			return screen == ScreenState.ON ? new Session(anchor, lastReadingMillis, tally, offTally) : new Session(anchor, lastReadingMillis, onTally, tally);
		}

		/** Closes the open stretch, up to the moment, into the tally of the state it was spent in; the anchor is left for the caller to move. */
		Session closedAt(Moment moment) {
			return withTally(anchor.screen(), tally(anchor.screen()).plusStretch(moment.millis() - anchor.millis(), anchor.level() - moment.level()));
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
	 * The screen-on and screen-off drain, side by side, and the overall drain over both.
	 *
	 * @param on      the screen-on drain
	 * @param off     the screen-off drain
	 * @param overall the drain in both states together, after its own warm-up ({@link #OVERALL_WARM_UP_MS}); the time remaining projects from its %/h
	 */
	public record ScreenSplit(ScreenDrain on, ScreenDrain off, ScreenDrain overall) {
		public static final ScreenSplit NONE = new ScreenSplit(ScreenDrain.NONE, ScreenDrain.NONE, ScreenDrain.NONE);
	}
}
