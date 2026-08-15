package com.almothafar.simplebatterynotifier.receiver;

import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.ChargeState;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.LevelAlertConfig;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.LevelAlertDecision;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.LevelAlertState;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.TemperatureDecision;
import com.almothafar.simplebatterynotifier.service.AlertType;
import com.almothafar.simplebatterynotifier.service.NotificationService;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link BatteryLevelReceiver}'s pure decision cores (#164), in the
 * {@code FastDrainDetectorTest} style: the critical/warning de-dupe, the red-alert override, the
 * full-once-per-charge episode with its re-arm band and its unplug dismissal, and the temperature
 * hysteresis. Because the state is now a value passed in and returned, every test doubles as a
 * process-restart test: the decision depends only on what was persisted, not on in-memory history.
 */
public class BatteryLevelReceiverDecisionTest {

	private static final int CRITICAL = 20;
	private static final int WARNING = 40;
	private static final int THRESHOLD_C = 45;

	private static final LevelAlertConfig DEFAULTS = new LevelAlertConfig(CRITICAL, WARNING, true, true, false);

	// The charge side of a broadcast. A completed charge reports BATTERY_STATUS_FULL rather than
	// CHARGING, so the full cases are "not charging" without being a discharge.
	private static final ChargeState DISCHARGING = new ChargeState(false, false, false);
	private static final ChargeState UNPLUGGED_STILL_FULL = new ChargeState(false, true, false);
	private static final ChargeState CHARGING = new ChargeState(true, false, true);
	private static final ChargeState FULL_ON_CHARGER = new ChargeState(false, true, true);

	// Episode state shorthands: nothing alerted yet, and "the full alert fired and is on screen".
	private static LevelAlertState fresh(int prevLevel, AlertType prevType) {
		return new LevelAlertState(prevLevel, prevType, false, false);
	}

	private static LevelAlertState fullAlertShowing(int prevLevel) {
		return new LevelAlertState(prevLevel, null, true, true);
	}

	// --- discharging: critical/warning thresholds and de-dupe -----------------------------------

	@Test
	public void discharging_belowCritical_firesCriticalOnce() {
		final LevelAlertDecision first = BatteryLevelReceiver.decideLevelAlert(
				fresh(16, null), 15, DISCHARGING, DEFAULTS);

		assertEquals(AlertType.CRITICAL, first.notifyType());
		assertEquals(fresh(15, AlertType.CRITICAL), first.newState());

		// Next tick, still below critical: the persisted prevType suppresses the duplicate.
		final LevelAlertDecision second = BatteryLevelReceiver.decideLevelAlert(
				first.newState(), 14, DISCHARGING, DEFAULTS);
		assertNull(second.notifyType());
		assertEquals(14, second.newState().prevLevel());
	}

	@Test
	public void discharging_inWarningBand_firesWarningOnce() {
		final LevelAlertDecision first = BatteryLevelReceiver.decideLevelAlert(
				fresh(41, null), 38, DISCHARGING, DEFAULTS);

		assertEquals(AlertType.WARNING, first.notifyType());

		final LevelAlertDecision second = BatteryLevelReceiver.decideLevelAlert(
				first.newState(), 35, DISCHARGING, DEFAULTS);
		assertNull(second.notifyType());
	}

	@Test
	public void discharging_aboveWarning_noAlert() {
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(81, null), 80, DISCHARGING, DEFAULTS);

		assertNull(d.notifyType());
		assertEquals(80, d.newState().prevLevel());
	}

	@Test
	public void discharging_warningDisabled_staysSilentInWarningBand() {
		final LevelAlertConfig noWarning = new LevelAlertConfig(CRITICAL, WARNING, false, true, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(41, null), 38, DISCHARGING, noWarning);

		assertNull(d.notifyType());
	}

	@Test
	public void discharging_warningThenCritical_escalates() {
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(35, AlertType.WARNING), 20, DISCHARGING, DEFAULTS);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void discharging_alertEveryTick_repeatsCritical() {
		final LevelAlertConfig everyTick = new LevelAlertConfig(CRITICAL, WARNING, true, true, true);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(15, AlertType.CRITICAL), 14, DISCHARGING, everyTick);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void discharging_atRedAlertLevel_reFiresCriticalDespiteDeDupe() {
		// Already alerted critical this episode, but at/below the red-alert level it must re-fire.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(5, AlertType.CRITICAL), NotificationService.RED_ALERT_LEVEL, DISCHARGING, DEFAULTS);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void unchangedLevel_skipsTheDischargeBranch() {
		// Same level as last tick routes to the charging-or-full branch (the receiver's historical
		// split), so a repeated broadcast at the same percentage can't duplicate a level alert.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(15, AlertType.CRITICAL), 15, DISCHARGING, DEFAULTS);

		assertNull(d.notifyType());
	}

	// --- full-battery alert: once per charge session, re-armed on the way down -------------------

	@Test
	public void charging_full_firesOnceThenHolds() {
		final LevelAlertDecision first = BatteryLevelReceiver.decideLevelAlert(
				fresh(100, null), 100, FULL_ON_CHARGER, DEFAULTS);

		assertEquals(AlertType.FULL, first.notifyType());
		assertTrue(first.newState().fullNotified());
		assertTrue(first.newState().fullAlertShown());

		final LevelAlertDecision second = BatteryLevelReceiver.decideLevelAlert(
				first.newState(), 100, FULL_ON_CHARGER, DEFAULTS);
		assertNull(second.notifyType());
	}

	@Test
	public void charging_fullDisabled_staysSilent() {
		final LevelAlertConfig noFull = new LevelAlertConfig(CRITICAL, WARNING, true, false, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(100, null), 100, FULL_ON_CHARGER, noFull);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
		assertFalse(d.newState().fullAlertShown());
	}

	@Test
	public void charging_levelLeavesFullBand_reArmsFullAlert() {
		// Notified at full, then the level drops to 90 (≤ FULL_PERCENTAGE, above warning): re-armed.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(90), 90, CHARGING, DEFAULTS);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void charging_belowWarningBand_doesNotReArmFullAlert() {
		// The re-arm band is (warning, FULL_PERCENTAGE]: charging low keeps the flag as-is.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(30), 30, CHARGING, DEFAULTS);

		assertTrue(d.newState().fullNotified());
	}

	@Test
	public void charging_reArmBand_doesNotForgetTheAlertIsStillOnScreen() {
		// The band re-arms the once-per-charge flag, but the notification is still up — conflating the
		// two is what left a stale full alert undismissable after a missed unplug.
		final LevelAlertDecision reArmed = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(95), 95, CHARGING, DEFAULTS);

		assertFalse(reArmed.newState().fullNotified());
		assertTrue(reArmed.newState().fullAlertShown());

		// Unplugged in that window, the alert is still dismissed.
		final LevelAlertDecision unplugged = BatteryLevelReceiver.decideLevelAlert(
				reArmed.newState(), 95, DISCHARGING, DEFAULTS);
		assertTrue(unplugged.clearFullAlert());
		assertFalse(unplugged.newState().fullAlertShown());
	}

	// --- unplugged: the full alert is bounded by the charger, not by the status --------------------

	@Test
	public void unplugged_stillReportingFull_neverFires() {
		// The status stays BATTERY_STATUS_FULL at 100% on plenty of devices with the cable already out.
		// Firing here is what made the alert look like it refused to go away: the unplug handler cleared
		// it and the very next broadcast posted it straight back.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(100, null), 100, UNPLUGGED_STILL_FULL, DEFAULTS);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
		assertFalse(d.clearFullAlert());
	}

	@Test
	public void unplugged_withFullAlertShown_dismissesItOnce() {
		// Missed unplug broadcast (process killed mid-charge): the shown alert is dismissed from
		// EXTRA_PLUGGED instead, then the session is re-armed so nothing dismisses twice.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(100), 100, UNPLUGGED_STILL_FULL, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
		assertFalse(d.newState().fullAlertShown());

		final LevelAlertDecision next = BatteryLevelReceiver.decideLevelAlert(
				d.newState(), 99, UNPLUGGED_STILL_FULL, DEFAULTS);
		assertFalse(next.clearFullAlert());
		assertNull(next.notifyType());
	}

	@Test
	public void unplugged_levelAlreadyDropping_stillDismissesTheFullAlert() {
		// The discharge branch (changed level) must clear it too — otherwise a restart that first sees
		// 99% leaves the alert up until the next charge session.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(100), 98, DISCHARGING, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void unplugged_dischargedToCritical_stillAlertsWhileDismissingTheStaleFull() {
		// Both at once: the receiver dismisses first and posts after, so the critical alert survives
		// (they share one notification ID). BatteryLevelReceiverTest pins that ordering.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(21), 19, DISCHARGING, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void unplugged_missedTransition_reArmsTheLevelDeDupeToo() {
		// The state-driven fallback must land in exactly the state onChargerDisconnected produces.
		// Re-arming only the full flag left prevType armed, silencing the next session's critical alert.
		final LevelAlertState afterFullCharge = new LevelAlertState(100, AlertType.CRITICAL, true, true);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				afterFullCharge, 100, UNPLUGGED_STILL_FULL, DEFAULTS);

		assertNull(d.newState().prevType());
		assertEquals(new LevelAlertState(100, null, false, false), d.newState());
	}

	@Test
	public void unplugged_missedTransition_criticalStillAlertsWithWarningsOff() {
		// The user-visible consequence of the re-arm above: with warning alerts disabled nothing else
		// resets prevType on the way down, so a half re-arm stayed silent all the way to RED_ALERT_LEVEL.
		final LevelAlertConfig noWarning = new LevelAlertConfig(CRITICAL, WARNING, false, true, false);
		final LevelAlertState afterFullCharge = new LevelAlertState(100, AlertType.CRITICAL, true, true);

		LevelAlertState state = BatteryLevelReceiver.decideLevelAlert(
				afterFullCharge, 100, UNPLUGGED_STILL_FULL, noWarning).newState();

		AlertType fired = null;
		for (int percentage = 99; percentage >= CRITICAL; percentage--) {
			final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(state, percentage, DISCHARGING, noWarning);
			state = d.newState();
			if (fired == null) {
				fired = d.notifyType();
			}
		}
		assertEquals(AlertType.CRITICAL, fired);
	}

	@Test
	public void unplugged_afterACriticalAlert_doesNotCancelIt() {
		// fullNotified survives a critical alert repainting the shared ID, so dismissal must key off
		// "is a full alert on screen" — otherwise the unplug silently cancels the live critical one.
		final LevelAlertState criticalOnScreen = new LevelAlertState(19, AlertType.CRITICAL, true, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				criticalOnScreen, 18, DISCHARGING, DEFAULTS);

		assertFalse(d.clearFullAlert());
	}

	@Test
	public void replugged_afterUnplugAtFull_firesAgain() {
		// The unplug re-arm is what lets a charger connected at 100% alert again, so it must survive
		// the dismissal path above.
		final LevelAlertDecision unplugged = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(100), 100, UNPLUGGED_STILL_FULL, DEFAULTS);
		final LevelAlertDecision replugged = BatteryLevelReceiver.decideLevelAlert(
				unplugged.newState(), 100, FULL_ON_CHARGER, DEFAULTS);

		assertEquals(AlertType.FULL, replugged.notifyType());
		assertFalse(replugged.clearFullAlert());
	}

	// --- charger-disconnect reset semantics (via the pure state) ---------------------------------

	@Test
	public void restartMidEpisode_persistedStateSuppressesDuplicates() {
		// Process death loses nothing: the decision on the persisted state after a "restart" is the
		// same as it would have been in-process — no duplicate critical while still below threshold.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fresh(15, AlertType.CRITICAL), 13, DISCHARGING, DEFAULTS);

		assertNull(d.notifyType());
	}

	// --- temperature hysteresis -------------------------------------------------------------------

	@Test
	public void temperature_aboveThreshold_firesOnce() {
		final TemperatureDecision first = BatteryLevelReceiver.decideTemperature(false, true, 460, THRESHOLD_C);
		assertTrue(first.shouldNotify());
		assertTrue(first.alerted());

		final TemperatureDecision second = BatteryLevelReceiver.decideTemperature(first.alerted(), true, 470, THRESHOLD_C);
		assertFalse(second.shouldNotify());
		assertTrue(second.alerted());
	}

	@Test
	public void temperature_inHysteresisBand_holdsState() {
		// 43.0 °C: below the 45° threshold but not yet 3° cooler — the alerted flag must hold, so a
		// process restart in this band can't re-fire when the temperature ticks back up.
		final TemperatureDecision d = BatteryLevelReceiver.decideTemperature(true, true, 430, THRESHOLD_C);
		assertFalse(d.shouldNotify());
		assertTrue(d.alerted());
	}

	@Test
	public void temperature_cooledBelowHysteresis_reArms() {
		final TemperatureDecision cooled = BatteryLevelReceiver.decideTemperature(true, true, 420, THRESHOLD_C);
		assertFalse(cooled.shouldNotify());
		assertFalse(cooled.alerted());

		// The next spell alerts again.
		final TemperatureDecision reAlert = BatteryLevelReceiver.decideTemperature(cooled.alerted(), true, 455, THRESHOLD_C);
		assertTrue(reAlert.shouldNotify());
	}

	@Test
	public void temperature_disabled_neverNotifiesAndReArms() {
		final TemperatureDecision d = BatteryLevelReceiver.decideTemperature(true, false, 470, THRESHOLD_C);
		assertFalse(d.shouldNotify());
		assertFalse(d.alerted());
	}
}
