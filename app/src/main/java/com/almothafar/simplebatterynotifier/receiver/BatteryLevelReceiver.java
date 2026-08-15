package com.almothafar.simplebatterynotifier.receiver;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import androidx.preference.PreferenceManager;
import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.service.AlertType;
import com.almothafar.simplebatterynotifier.service.BatteryHealthTracker;
import com.almothafar.simplebatterynotifier.service.BatteryRateTracker;
import com.almothafar.simplebatterynotifier.service.BatteryTemperatureTracker;
import com.almothafar.simplebatterynotifier.service.FastDrainDetector;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.service.SlowChargeDetector;
import com.almothafar.simplebatterynotifier.service.SystemService;
import com.almothafar.simplebatterynotifier.util.AppPrefs;
import com.almothafar.simplebatterynotifier.util.TemperatureUtils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Broadcast receiver for monitoring battery level changes.
 * Sends notifications when battery reaches critical/warning levels or becomes full.
 * <p>
 * Alert episode state (level-alert de-dupe, full-once-per-charge, temperature hysteresis) is
 * persisted in {@link SharedPreferences} so it survives process death (#164) — doze, OEM task
 * killers and memory pressure routinely kill a background-monitoring app, and in-memory state
 * previously reset mid-episode, firing duplicate alerts. The logic follows the same
 * load → pure decide → save-on-change pattern as {@link FastDrainDetector} and
 * {@link SlowChargeDetector}.
 * <p>
 * <b>Threading:</b> there is deliberately no lock. Both this receiver and the unplug reset
 * ({@link PowerConnectionReceiver} → {@link #onChargerDisconnected}) are registered on and
 * delivered to the main thread (see {@code PowerConnectionService}), so all state access is
 * serialized by the main looper.
 */
public class BatteryLevelReceiver extends BroadcastReceiver {

	/**
	 * How far (in °C) the battery must cool below the threshold before another high-temperature
	 * alert can fire. Hysteresis prevents repeated alerts during a single hot spell.
	 */
	private static final int TEMPERATURE_HYSTERESIS_C = 3;

	// Persisted alert episode state (survives process restarts, #164).
	private static final String PREF_PREV_LEVEL = "_level_alert_prev_level";
	private static final String PREF_PREV_TYPE = "_level_alert_prev_type";
	private static final String PREF_FULL_NOTIFIED = "_level_alert_full_notified";
	private static final String PREF_FULL_ALERT_SHOWN = "_level_alert_full_shown";
	private static final String PREF_TEMPERATURE_ALERTED = "_temperature_alert_sent";

	/**
	 * Charger-disconnect reset: re-arms the alerts whose episode is bounded by a charge session —
	 * the full-battery alert and the critical/warning de-dupe — so the new discharge session can
	 * alert afresh. Deliberately <b>not</b> reset (#164):
	 * <ul>
	 *   <li>{@code prevLevel} — the next broadcast still compares against the real last-seen level,
	 *       so an unchanged level keeps skipping the discharge branch;</li>
	 *   <li>the temperature flag — a hot spell doesn't end at unplug; cooling below the threshold
	 *       (the hysteresis in {@link #decideTemperature}) is its only re-arm.</li>
	 * </ul>
	 * This is the prompt path, driven by the unplug transition. It is not the only one:
	 * {@link #decideLevelAlert} applies the very same {@link #reArmForNewDischarge} reset — and
	 * dismisses a stale full alert — as soon as a broadcast reports the charger out, so a transition
	 * missed while the process was dead resolves identically rather than only half-resolving.
	 *
	 * @param context The application context
	 */
	public static void onChargerDisconnected(Context context) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final LevelAlertState state = loadLevelState(prefs);
		saveLevelStateIfChanged(prefs, state, reArmForNewDischarge(state));
	}

	/**
	 * The charge-session reset both unplug paths share: re-arm the full-battery alert and the
	 * critical/warning de-dupe, and forget any full alert that was on screen. Keeping this in one
	 * place is what makes the transition path ({@link #onChargerDisconnected}) and the state-driven
	 * fallback in {@link #decideLevelAlert} provably equivalent.
	 *
	 * @param state the episode state to reset
	 *
	 * @return the state a fresh discharge session starts from
	 */
	private static LevelAlertState reArmForNewDischarge(LevelAlertState state) {
		return new LevelAlertState(state.prevLevel(), null, false, false);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		// The receiver is registered for ACTION_BATTERY_CHANGED (see PowerConnectionService), so the
		// delivered intent already carries the battery state — no need to re-query the sticky
		// broadcast (#159). The action check guards against unexpected/spoofed intents.
		if (isNull(intent) || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
			return;
		}

		final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
		// The cable, not the status, is what bounds the full-battery alert: plenty of devices keep
		// reporting BATTERY_STATUS_FULL while sitting at 100% with the charger already out. See
		// decideLevelAlert.
		final ChargeState power = new ChargeState(
				status == BatteryManager.BATTERY_STATUS_CHARGING,
				status == BatteryManager.BATTERY_STATUS_FULL,
				status == BatteryManager.BATTERY_STATUS_NOT_CHARGING,
				intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0);

		// Build the reading from the delivered intent; with a non-null intent this never returns null.
		final BatteryDO batteryDO = SystemService.getBatteryInfo(context, intent);

		// Feed the charge/drain rate window from this broadcast (no polling timer of our own) so both the
		// ongoing notification below and the details table reflect the latest reading (issue #108). The
		// fast-drain alert (#109) then evaluates the same smoothed rate.
		final BatteryRateTracker.BatteryRate rate = BatteryRateTracker.record(context, batteryDO);

		// Keep the persistent foreground-service status notification live with the latest reading,
		// reusing the rate just computed instead of re-parsing the persisted sample window.
		NotificationService.updateOngoingNotification(context, batteryDO, rate);

		final int percentage = batteryDO.getBatteryPercentageInt();

		// Track battery health and charge cycles
		BatteryHealthTracker.recordBatteryState(context, percentage, status);

		// #260: keep the coldest/hottest reading of the current charge for the Insights temperature
		// range. The whole reading goes in — finishing a charge is what starts a new range, so the
		// tracker needs the level and status as well as the temperature.
		BatteryTemperatureTracker.record(context, batteryDO);

		final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

		handleLevelAlerts(context, sharedPref, percentage, power);
		handleTemperature(context, batteryDO, sharedPref);

		// #109: warn when the (smoothed #108) drain rate stays abnormally high for a sustained time.
		FastDrainDetector.evaluate(context, batteryDO, rate);

		// #123: warn when charging power stays abnormally low for a sustained time (frayed cable, dirty
		// port, or dying charger). Independent of the drain rate — it reads the estimated charge wattage.
		SlowChargeDetector.evaluate(context, batteryDO);
	}

	/**
	 * Decide and dispatch this broadcast's critical/warning/full level alert.
	 * <p>
	 * Dismissal runs <b>before</b> dispatch: the level alerts share one notification ID, so an alert
	 * decided on this same broadcast must replace the stale full one rather than be cancelled after
	 * it. {@code BatteryLevelReceiverTest} pins that order.
	 *
	 * @param context    The application context
	 * @param sharedPref The shared preferences
	 * @param percentage Current battery percentage
	 * @param power      What this broadcast says about charging, fullness and the cable
	 */
	private void handleLevelAlerts(Context context, SharedPreferences sharedPref, int percentage, ChargeState power) {
		final LevelAlertConfig config = new LevelAlertConfig(
				AppPrefs.criticalLevel(context),
				AppPrefs.warningLevel(context),
				AppPrefs.chargeTarget(context),
				sharedPref.getBoolean(context.getString(R.string._pref_key_notify_for_warning_level), true),
				sharedPref.getBoolean(context.getString(R.string._pref_key_notify_for_full_level), true),
				sharedPref.getBoolean(context.getString(R.string._pref_key_notify_every_tick), false));

		final LevelAlertState previous = loadLevelState(sharedPref);
		final LevelAlertDecision decision = decideLevelAlert(previous, percentage, power, config);

		// Persisted before the notification is posted, so a kill in between can only leave the
		// recoverable "flag says shown, nothing is" state — never the reverse (#268).
		saveLevelStateIfChanged(sharedPref, previous, decision.newState());
		if (decision.clearFullAlert()) {
			NotificationService.clearLevelAlert(context);
		}
		if (nonNull(decision.notifyType())) {
			NotificationService.sendNotification(context, decision.notifyType(), percentage);
		}
	}

	/**
	 * Send a high-temperature safety alert when the battery exceeds the configured threshold, and
	 * dismiss it again once the hot spell ends.
	 * <p>
	 * The hysteresis flag is persisted (#164) so a process restart mid-hot-spell cannot fire a
	 * duplicate alert; another alert can only fire once the battery has cooled at least
	 * {@link #TEMPERATURE_HYSTERESIS_C}°C below the threshold. That same re-arm is the end of the
	 * spell, so it also takes the shown alert down (#259).
	 *
	 * @param context    The application context
	 * @param batteryDO  Current battery snapshot (non-null; the caller already checked)
	 * @param sharedPref The shared preferences
	 */
	private void handleTemperature(Context context, BatteryDO batteryDO, SharedPreferences sharedPref) {
		final boolean enabled = sharedPref.getBoolean(context.getString(R.string._pref_key_notify_high_temperature), true);

		// The threshold is stored canonically in Celsius; the battery reading is also Celsius
		// (tenths), so a chilly 45 °F (~7 °C) never trips a 45 °C threshold. See TemperatureUtils.
		final int thresholdCelsius = sharedPref.getInt(
				context.getString(R.string._pref_key_high_temperature_threshold),
				TemperatureUtils.DEFAULT_HIGH_TEMP_THRESHOLD_C);
		final int rawTenthsC = batteryDO.getTemperature();

		final boolean previouslyAlerted = sharedPref.getBoolean(PREF_TEMPERATURE_ALERTED, false);
		final TemperatureDecision decision = decideTemperature(previouslyAlerted, enabled, rawTenthsC, thresholdCelsius);

		if (decision.alerted() != previouslyAlerted) {
			persistTemperatureAlerted(sharedPref, decision.alerted());
		}
		if (decision.shouldNotify()) {
			NotificationService.sendTemperatureNotification(context, rawTenthsC);
		} else if (previouslyAlerted && !decision.alerted()) {
			// The spell just ended: cooled past the hysteresis, or the alert switched off while one was
			// showing. Dismiss the stale warning (#259) — staying inside the band holds, so it can't flap.
			NotificationService.clearTemperatureAlert(context);
		}
	}

	/**
	 * Persist the hot-spell flag, synchronously when it is being turned <em>on</em> (#268).
	 * <p>
	 * The flag is written before the notification is posted so that a process killed in between leaves
	 * the recoverable state — a flag claiming an alert that isn't showing, which the next cool tick
	 * resolves with a no-op cancel. {@code apply()} does not actually give that ordering: it returns
	 * before the write reaches disk, so a hard kill can lose the flag while the notification, living in
	 * system_server, survives. The alert is then stranded with nothing left to dismiss it. Turning the
	 * flag <em>off</em> keeps {@code apply()} — losing that write is self-healing, because the next tick
	 * re-decides the same transition and repeats the cancel.
	 * <p>
	 * Only a hot-spell transition reaches here (the caller compares against the stored value), so this
	 * is roughly two synchronous writes per spell, never one per broadcast.
	 *
	 * @param sharedPref The shared preferences
	 * @param alerted    the flag value to store; {@code true} means an alert is now on screen
	 */
	@SuppressLint("ApplySharedPref") // Durability is the point — see above; the write is per-spell, not per-tick
	static void persistTemperatureAlerted(SharedPreferences sharedPref, boolean alerted) {
		final SharedPreferences.Editor edit = sharedPref.edit().putBoolean(PREF_TEMPERATURE_ALERTED, alerted);
		if (alerted) {
			edit.commit();
		} else {
			edit.apply();
		}
	}

	/**
	 * Pure decision core for the critical/warning/full level alerts, unit-testable with no Android
	 * dependencies (#164). A changed level while discharging is judged against the thresholds; an
	 * unchanged level or a charging/full state runs the full-battery logic instead — the same split
	 * the receiver has always used, now explicit.
	 * <p>
	 * The full-battery alert is bounded by the <em>charger</em>, not by the status: a battery sitting
	 * at 100% keeps reporting {@code BATTERY_STATUS_FULL} on many devices long after the cable is out.
	 * Unplugged, therefore, the alert can neither fire (it would re-post itself the instant the unplug
	 * dismissal cleared it, reading as a notification that refuses to go away) nor stay shown — "you
	 * can unplug now" is answered once the charger is out, so a displayed one is dismissed here. That
	 * dismissal is deliberately driven by {@code EXTRA_PLUGGED} rather than left to the unplug
	 * broadcast alone: when the process is killed mid-charge (doze, OEM task killers) nobody sees that
	 * transition, and the alert would otherwise linger — un-swipeable, when the user has the
	 * sticky-notification preference on.
	 * <p>
	 * Closing the episode is one-shot and applies {@link #reArmForNewDischarge}, so the fallback ends in exactly the state the seen transition would have
	 * produced. Dismissal keys off {@code fullAlertShown} rather than {@code fullNotified}: the latter re-arms mid-charge inside the
	 * {@code (warning, target − margin]} band while the notification is still on screen, and it stays set after a critical alert has repainted the shared ID —
	 * reading it would both strand stale full alerts and cancel live critical ones.
	 *
	 * @param state      current persisted episode state
	 * @param percentage current battery percentage (whole, via the single rounding policy #158)
	 * @param power      what this broadcast says about charging, fullness and the cable
	 * @param config     the user's alert thresholds and toggles
	 *
	 * @return which alert to send now ({@code null} for none), whether to dismiss a stale full alert,
	 * and the new state to persist
	 */
	static LevelAlertDecision decideLevelAlert(LevelAlertState state, int percentage, ChargeState power, LevelAlertConfig config) {
		// Off the charger the charge session is over: re-arm exactly as the unplug transition does, and
		// dismiss the full alert if one is still occupying the shared level-alert notification.
		final boolean chargeSessionOver = !power.plugged() && (state.fullNotified() || state.fullAlertShown());
		final boolean dismissFullAlert = !power.plugged() && state.fullAlertShown();
		final LevelAlertState episode = chargeSessionOver ? reArmForNewDischarge(state) : state;

		final boolean levelChanged = episode.prevLevel() != percentage;
		final LevelAlertDecision decision = levelChanged && !power.charging()
		                                    ? decideDischarging(episode, percentage, config)
		                                    : decideChargingOrFull(episode, percentage, power, config);

		return decision.withClearFullAlert(dismissFullAlert);
	}

	/**
	 * The discharging side: critical first, then warning, de-duplicated via {@code prevType} —
	 * except at/below the red-alert floor, where the critical alert always re-fires.
	 */
	private static LevelAlertDecision decideDischarging(LevelAlertState state, int percentage, LevelAlertConfig config) {
		// Force the critical alert through the de-dupe at the red-alert floor: about to die trumps "already told you".
		AlertType prevType = percentage <= NotificationService.RED_ALERT_LEVEL ? null : state.prevType();
		AlertType notifyType = null;

		if (percentage <= config.criticalLevel()) {
			if (prevType != AlertType.CRITICAL || config.alertEveryTick()) {
				notifyType = AlertType.CRITICAL;
			}
			prevType = AlertType.CRITICAL;
		} else if (percentage <= config.warningLevel() && config.warningEnabled()) {
			if (prevType != AlertType.WARNING) {
				notifyType = AlertType.WARNING;
			}
			prevType = AlertType.WARNING;
		}
		// A critical/warning alert repaints the shared level-alert ID, so whatever full alert was on
		// screen is gone — and must not be "dismissed" later, which would cancel this one instead.
		final boolean fullAlertShown = state.fullAlertShown() && isNull(notifyType);
		return new LevelAlertDecision(notifyType,
				new LevelAlertState(percentage, prevType, state.fullNotified(), fullAlertShown));
	}

	/**
	 * The charging-or-unchanged side: the full alert fires once per charge session, re-armed once
	 * the level has genuinely dropped out of the full band while staying above the warning band.
	 * <p>
	 * "Full" is the user's <b>charge target</b> (#263), not only a completed charge: at the default 90 the alert arrives while the battery is still climbing,
	 * which is the point — being told to unplug at 100% is being told after the wear the alert warns about has happened. Reaching the target only counts
	 * <em>while charging</em>; see {@link ChargeState#reachedChargeTarget}.
	 * <p>
	 * The re-arm band moves with the target ({@link AppPrefs#reArmLevel}) rather than sitting at a fixed 95%. A fixed point below the target would re-arm on
	 * the very tick the alert fired and then fire again on every percent up to 100 — seven notifications for one charge at a target of 90.
	 *
	 * @param state      current persisted episode state
	 * @param percentage current battery percentage
	 * @param power      what this broadcast says about charging, fullness and the cable — the status
	 *                   alone is not enough, see {@link #decideLevelAlert}
	 * @param config     the user's alert thresholds and toggles
	 */
	private static LevelAlertDecision decideChargingOrFull(LevelAlertState state, int percentage, ChargeState power, LevelAlertConfig config) {
		// One reading of "the charge you asked for is done", used by both the fire condition and the re-arm below. They are exact opposites, and evaluating the
		// predicate twice invites them to drift apart — which is the bug the re-arm comment describes.
		final boolean chargeDone = power.reachedChargeTarget(percentage, config.chargeTarget());

		boolean fullNotified = state.fullNotified();
		boolean fullAlertShown = state.fullAlertShown();
		AlertType notifyType = null;

		if (!fullNotified && config.fullNotifyEnabled() && chargeDone) {
			notifyType = AlertType.FULL;
			fullNotified = true;
			fullAlertShown = true;
		}
		// Only the once-per-charge flag re-arms here. Whether the alert is still on screen is a separate fact: the battery drifting down out of the band
		// doesn't take the notification away, and conflating the two left stale full alerts undismissable after a missed unplug.
		//
		// A broadcast that still reports the charge done never re-arms, whatever the level says. A device whose charge cap completes the charge below the
		// re-arm level — Samsung's "Protect battery" at 85%, Sony and Asus at 80 — otherwise satisfies the fire condition and the re-arm band at the same time,
		// so the flag cleared as fast as it was set and the alert re-posted for as long as the cable was in: undismissable, and audible on every tick for
		// anyone using the silent-mode override. Leaving the charge behind is what re-arms, so "still there" must not. BatteryTemperatureTracker.nextFullSeen
		// guards the identical hazard the same way.
		if (!chargeDone && percentage <= AppPrefs.reArmLevel(config.chargeTarget()) && percentage > config.warningLevel()) {
			fullNotified = false;
		}
		return new LevelAlertDecision(notifyType,
				new LevelAlertState(percentage, state.prevType(), fullNotified, fullAlertShown));
	}

	/**
	 * Pure decision core for the high-temperature alert's hysteresis (#18, #164):
	 * <ul>
	 *   <li><b>Disabled</b> — never notify, and re-arm so re-enabling starts fresh;</li>
	 *   <li><b>At/above the threshold</b> — notify only if not already alerted this hot spell;</li>
	 *   <li><b>Cooled below threshold − hysteresis</b> — re-arm for the next spell;</li>
	 *   <li><b>In the hysteresis band between them</b> — hold the current state.</li>
	 * </ul>
	 * Re-arming doubles as "the spell is over": the caller dismisses the shown alert on that
	 * transition (#259), which is why the disabled case re-arms too rather than just going quiet.
	 *
	 * @param alreadyAlerted   whether this hot spell's alert has already fired
	 * @param enabled          whether the high-temperature alert is enabled
	 * @param rawTenthsC       battery temperature in tenths of a degree Celsius
	 * @param thresholdCelsius alert threshold in whole degrees Celsius
	 *
	 * @return whether to notify now, and the new alerted flag to persist
	 */
	static TemperatureDecision decideTemperature(boolean alreadyAlerted, boolean enabled, int rawTenthsC, int thresholdCelsius) {
		if (!enabled) {
			return new TemperatureDecision(false, false);
		}
		if (TemperatureUtils.isAtOrAboveThreshold(rawTenthsC, thresholdCelsius)) {
			return new TemperatureDecision(!alreadyAlerted, true);
		}
		if (TemperatureUtils.isBelowResetThreshold(rawTenthsC, thresholdCelsius, TEMPERATURE_HYSTERESIS_C)) {
			return new TemperatureDecision(false, false);
		}
		return new TemperatureDecision(false, alreadyAlerted);
	}

	static LevelAlertState loadLevelState(SharedPreferences prefs) {
		return new LevelAlertState(
				prefs.getInt(PREF_PREV_LEVEL, 0),
				AlertType.fromPersistedId(prefs.getInt(PREF_PREV_TYPE, 0)),
				prefs.getBoolean(PREF_FULL_NOTIFIED, false),
				prefs.getBoolean(PREF_FULL_ALERT_SHOWN, false));
	}

	static void saveLevelState(SharedPreferences prefs, LevelAlertState state) {
		levelStateEditor(prefs, state).apply();
	}

	/**
	 * Persists synchronously, for the write that records a full alert reaching the screen (#268).
	 * <p>
	 * {@code apply()} returns before the write lands, so a process killed between it and the notification
	 * loses {@code fullAlertShown} while the notification, living in system_server, survives. The
	 * state-driven dismissal in {@link #decideLevelAlert} keys on exactly that flag, so it would then read
	 * "nothing on screen" and leave the alert up for good.
	 *
	 * @param prefs the shared preferences
	 * @param state the episode state to persist
	 */
	@SuppressLint("ApplySharedPref") // Durability is the point — see above; only on the alert transition
	private static void saveLevelStateDurably(SharedPreferences prefs, LevelAlertState state) {
		levelStateEditor(prefs, state).commit();
	}

	private static SharedPreferences.Editor levelStateEditor(SharedPreferences prefs, LevelAlertState state) {
		return prefs.edit()
		            .putInt(PREF_PREV_LEVEL, state.prevLevel())
		            .putInt(PREF_PREV_TYPE, AlertType.persistedId(state.prevType()))
		            .putBoolean(PREF_FULL_NOTIFIED, state.fullNotified())
		            .putBoolean(PREF_FULL_ALERT_SHOWN, state.fullAlertShown());
	}

	/**
	 * Persist the episode state only when it differs from what was loaded — most broadcasts
	 * (voltage/temperature deltas) re-decide an identical state, and rewriting it would churn
	 * SharedPreferences on every tick.
	 * <p>
	 * The transition that puts a full alert on screen goes through {@link #saveLevelStateDurably};
	 * everything else, the re-arm included, stays asynchronous. Losing a re-arm write is self-healing —
	 * the next tick re-decides it — so only the one unrecoverable write pays for the disk wait.
	 *
	 * @param prefs    the shared preferences
	 * @param previous the state loaded at the start of this tick
	 * @param state    the state to persist
	 */
	static void saveLevelStateIfChanged(SharedPreferences prefs, LevelAlertState previous, LevelAlertState state) {
		if (state.equals(previous)) {
			return;
		}
		if (!previous.fullAlertShown() && state.fullAlertShown()) {
			saveLevelStateDurably(prefs, state);
		} else {
			saveLevelState(prefs, state);
		}
	}

	/**
	 * Persisted level-alert episode state (#164). The type is stored via its stable persisted id
	 * (see {@link AlertType#persistedId}), so state written by older int-typed versions reads back
	 * unchanged.
	 *
	 * @param prevLevel      the percentage seen on the previous broadcast (gates the discharge branch)
	 * @param prevType       the last level alert sent while discharging ({@code null} when none)
	 * @param fullNotified   whether the full-battery alert has fired this charge session — the
	 * once-per-charge de-dupe, re-armed by the {@code (warning, target − margin]} band (see {@link AppPrefs#reArmLevel})
	 * @param fullAlertShown whether a full alert currently occupies the shared level-alert
	 *                       notification. Distinct from {@code fullNotified}: the band re-arms that
	 *                       one while the notification is still up, and a critical/warning alert
	 *                       repaints the ID without ending the charge session
	 */
	record LevelAlertState(int prevLevel, AlertType prevType, boolean fullNotified, boolean fullAlertShown) {
	}

	/**
	 * What one {@code ACTION_BATTERY_CHANGED} broadcast says about the charge side. These three
	 * always travel together and are all read from the same intent, so they are one value rather
	 * than three adjacent booleans at every call site.
	 *
	 * @param charging    whether the battery status is {@code BATTERY_STATUS_CHARGING}
	 * @param full        whether the battery status is {@code BATTERY_STATUS_FULL}
	 * @param notCharging whether the battery status is {@code BATTERY_STATUS_NOT_CHARGING} — the cable
	 * is in but nothing is flowing, which is how a device parked at an OEM charge cap reports itself when it doesn't claim to be full
	 * @param plugged     whether a charger is connected ({@code EXTRA_PLUGGED} is non-zero)
	 */
	record ChargeState(boolean charging, boolean full, boolean notCharging, boolean plugged) {

		/**
		 * Charge complete <em>and</em> still on the charger — what actually bounds the full-battery
		 * alert. The status alone is not enough: many devices keep reporting
		 * {@code BATTERY_STATUS_FULL} at 100% with the cable already out.
		 *
		 * @return true when the full-battery alert may fire
		 */
		boolean fullOnCharger() {
			return full && plugged;
		}

		/**
		 * Whether this broadcast means "the charge you asked for is done" (#263): either a completed charge on the charger, or a battery still charging that
		 * has climbed to the user's target.
		 * <p>
		 * The gate is on the battery being <em>fed</em>, not merely on the level. {@code decideChargingOrFull} is also reached on the "level unchanged while
		 * discharging" path, where the old {@code full} status flag was harmless — {@code percentage >= target} is not, and sitting at 90% on the way
		 * <em>down</em> would announce a charge that never happened.
		 * <p>
		 * {@code NOT_CHARGING} counts alongside {@code CHARGING}, because that is what a device holding at its own charge cap reports (Samsung "Protect
		 * battery", Sony, Asus): cable in, nothing flowing, and never a {@code FULL} status to fire on. Requiring {@code CHARGING} left the feature silently
		 * dead on exactly the devices whose owners care most about a charge target. {@code DISCHARGING} and {@code UNKNOWN} stay out — a battery going down is
		 * not a charge finishing, whatever the cable says.
		 * <p>
		 * At the maximum target the level is deliberately <b>not</b> consulted: 100% means "wait for a genuinely complete charge", and the battery saying so is
		 * the only trustworthy signal for that. Firing on the level there would alert a tick early on OEMs that reach 100% with the status still
		 * {@code CHARGING} — a changed alert for someone who never touched the slider.
		 *
		 * @param percentage current battery percentage
		 * @param target     the user's charge target in percent
		 *
		 * @return true when the full-battery alert may fire
		 */
		boolean reachedChargeTarget(int percentage, int target) {
			if (fullOnCharger()) {
				return true;
			}
			return !AppPrefs.targetIsAFullCharge(target)
					&& plugged && (charging || notCharging)
					&& percentage >= target;
		}
	}

	/**
	 * The user's alert thresholds and toggles (reduces parameter count, like
	 * {@code NotificationConfig}).
	 *
	 * @param criticalLevel     critical alert threshold in percent
	 * @param warningLevel      warning alert threshold in percent
	 * @param chargeTarget      the level the full-battery alert fires at while charging (#263)
	 * @param warningEnabled    whether the warning alert is enabled
	 * @param fullNotifyEnabled whether the full-battery alert is enabled
	 * @param alertEveryTick    whether the critical alert repeats on every level tick
	 */
	record LevelAlertConfig(
			int criticalLevel,
			int warningLevel,
			int chargeTarget,
			boolean warningEnabled,
			boolean fullNotifyEnabled,
			boolean alertEveryTick) {
	}

	/**
	 * Result of {@link #decideLevelAlert}: which alert to send now, whether a stale full alert is
	 * still on screen, and the state to persist.
	 *
	 * @param notifyType     the alert to send, or {@code null} for none
	 * @param newState       the state to persist
	 * @param clearFullAlert whether a full-battery alert shown from a finished charge session must be
	 *                       dismissed (the charger is out)
	 */
	record LevelAlertDecision(AlertType notifyType, LevelAlertState newState, boolean clearFullAlert) {

		/**
		 * A decision from one of the branch helpers, which see a single broadcast's level and cannot
		 * know whether a finished charge session left an alert on screen — only
		 * {@link #decideLevelAlert} decides that.
		 *
		 * @param notifyType the alert to send, or {@code null} for none
		 * @param newState   the state to persist
		 */
		LevelAlertDecision(AlertType notifyType, LevelAlertState newState) {
			this(notifyType, newState, false);
		}

		/**
		 * This decision with the dismissal the caller determined.
		 *
		 * @param clear whether a stale full alert must be dismissed
		 *
		 * @return the same decision carrying {@code clear}
		 */
		LevelAlertDecision withClearFullAlert(boolean clear) {
			return new LevelAlertDecision(notifyType, newState, clear);
		}
	}

	/**
	 * Result of {@link #decideTemperature}: whether to notify now, and the flag to persist.
	 *
	 * @param shouldNotify whether to send the temperature alert now
	 * @param alerted      whether the current hot spell counts as alerted
	 */
	record TemperatureDecision(boolean shouldNotify, boolean alerted) {
	}
}
