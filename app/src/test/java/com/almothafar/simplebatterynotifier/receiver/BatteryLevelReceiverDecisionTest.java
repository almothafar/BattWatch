package com.almothafar.simplebatterynotifier.receiver;

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

	private static final boolean DISCHARGING = false;
	// A completed charge reports BATTERY_STATUS_FULL rather than CHARGING, so the full cases are
	// "not charging" without being a discharge.
	private static final boolean NOT_CHARGING = false;
	private static final boolean CHARGING = true;
	private static final boolean NOT_FULL = false;
	private static final boolean FULL = true;
	private static final boolean PLUGGED = true;
	private static final boolean UNPLUGGED = false;

	// --- discharging: critical/warning thresholds and de-dupe -----------------------------------

	@Test
	public void discharging_belowCritical_firesCriticalOnce() {
		final LevelAlertDecision first = BatteryLevelReceiver.decideLevelAlert(
				new LevelAlertState(16, null, false), 15, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

		assertEquals(AlertType.CRITICAL, first.notifyType());
		assertEquals(new LevelAlertState(15, AlertType.CRITICAL, false), first.newState());

		// Next tick, still below critical: the persisted prevType suppresses the duplicate.
		final LevelAlertDecision second = BatteryLevelReceiver.decideLevelAlert(
				first.newState(), 14, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);
		assertNull(second.notifyType());
		assertEquals(14, second.newState().prevLevel());
	}

	@Test
	public void discharging_inWarningBand_firesWarningOnce() {
		final LevelAlertDecision first = BatteryLevelReceiver.decideLevelAlert(
				new LevelAlertState(41, null, false), 38, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

		assertEquals(AlertType.WARNING, first.notifyType());

		final LevelAlertDecision second = BatteryLevelReceiver.decideLevelAlert(
				first.newState(), 35, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);
		assertNull(second.notifyType());
	}

	@Test
	public void discharging_aboveWarning_noAlert() {
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				new LevelAlertState(81, null, false), 80, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

		assertNull(d.notifyType());
		assertEquals(80, d.newState().prevLevel());
	}

	@Test
	public void discharging_warningDisabled_staysSilentInWarningBand() {
		final LevelAlertConfig noWarning = new LevelAlertConfig(CRITICAL, WARNING, false, true, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				new LevelAlertState(41, null, false), 38, DISCHARGING, NOT_FULL, UNPLUGGED, noWarning);

		assertNull(d.notifyType());
	}

	@Test
	public void discharging_warningThenCritical_escalates() {
		final LevelAlertState afterWarning = new LevelAlertState(35, AlertType.WARNING, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				afterWarning, 20, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void discharging_alertEveryTick_repeatsCritical() {
		final LevelAlertConfig everyTick = new LevelAlertConfig(CRITICAL, WARNING, true, true, true);
		final LevelAlertState alreadyCritical = new LevelAlertState(15, AlertType.CRITICAL, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				alreadyCritical, 14, DISCHARGING, NOT_FULL, UNPLUGGED, everyTick);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void discharging_atRedAlertFloor_overridesDeDupe() {
		// Already alerted critical this episode, but at/below the red-alert level it must re-fire.
		final LevelAlertState alreadyCritical = new LevelAlertState(5, AlertType.CRITICAL, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				alreadyCritical, NotificationService.RED_ALERT_LEVEL, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void discharging_unchangedLevel_doesNotAlert() {
		// Same level as last tick routes to the charging-or-full branch (the receiver's historical
		// split), so a repeated broadcast at the same percentage can't duplicate a level alert.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				new LevelAlertState(15, AlertType.CRITICAL, false), 15, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

		assertNull(d.notifyType());
	}

	// --- charging / full: once per charge session ------------------------------------------------

	@Test
	public void charging_full_firesOnceThenHolds() {
		final LevelAlertState atHundred = new LevelAlertState(100, null, false);
		final LevelAlertDecision first = BatteryLevelReceiver.decideLevelAlert(
				atHundred, 100, NOT_CHARGING, FULL, PLUGGED, DEFAULTS);

		assertEquals(AlertType.FULL, first.notifyType());
		assertTrue(first.newState().fullNotified());

		final LevelAlertDecision second = BatteryLevelReceiver.decideLevelAlert(
				first.newState(), 100, NOT_CHARGING, FULL, PLUGGED, DEFAULTS);
		assertNull(second.notifyType());
	}

	@Test
	public void charging_fullDisabled_staysSilent() {
		final LevelAlertConfig noFull = new LevelAlertConfig(CRITICAL, WARNING, true, false, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				new LevelAlertState(100, null, false), 100, NOT_CHARGING, FULL, PLUGGED, noFull);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void charging_levelLeavesFullBand_reArmsFullAlert() {
		// Notified at full, then the level drops to 90 (≤ FULL_PERCENTAGE, above warning): re-armed.
		final LevelAlertState notified = new LevelAlertState(100, null, true);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				notified, 90, CHARGING, NOT_FULL, PLUGGED, DEFAULTS);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void charging_belowWarningBand_doesNotReArmFullAlert() {
		// The re-arm band is (warning, FULL_PERCENTAGE]: charging low keeps the flag as-is.
		final LevelAlertState notified = new LevelAlertState(100, null, true);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				notified, 30, CHARGING, NOT_FULL, PLUGGED, DEFAULTS);

		assertTrue(d.newState().fullNotified());
	}

	// --- unplugged: the full alert is bounded by the charger, not by the status --------------------

	@Test
	public void unplugged_stillReportingFull_neverFires() {
		// The status stays BATTERY_STATUS_FULL at 100% on plenty of devices with the cable already out.
		// Firing here is what made the alert look like it refused to go away: the unplug handler cleared
		// it and the very next broadcast posted it straight back.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				new LevelAlertState(100, null, false), 100, NOT_CHARGING, FULL, UNPLUGGED, DEFAULTS);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
		assertFalse(d.clearFullAlert());
	}

	@Test
	public void unplugged_withFullAlertShown_dismissesItOnce() {
		// Missed unplug broadcast (process killed mid-charge): the shown alert is dismissed from the
		// plugged state instead, then the episode is re-armed so nothing dismisses twice.
		final LevelAlertState notified = new LevelAlertState(100, null, true);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				notified, 100, NOT_CHARGING, FULL, UNPLUGGED, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());

		final LevelAlertDecision next = BatteryLevelReceiver.decideLevelAlert(
				d.newState(), 99, DISCHARGING, FULL, UNPLUGGED, DEFAULTS);
		assertFalse(next.clearFullAlert());
		assertNull(next.notifyType());
	}

	@Test
	public void unplugged_levelAlreadyDropping_stillDismissesTheFullAlert() {
		// The discharge branch (changed level) must clear it too — otherwise a restart that first sees
		// 99% leaves the alert up until the next charge session.
		final LevelAlertState notified = new LevelAlertState(100, null, true);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				notified, 98, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void unplugged_dischargedToCritical_stillAlertsWhileDismissingTheStaleFull() {
		// Both at once: the receiver dismisses first and posts after, so the critical alert survives
		// (they share one notification ID).
		final LevelAlertState notified = new LevelAlertState(21, null, true);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				notified, 19, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void replugged_afterUnplugAtFull_firesAgain() {
		// The unplug re-arm is what lets a charger connected at 100% alert again, so it must survive
		// the dismissal path above.
		final LevelAlertState notified = new LevelAlertState(100, null, true);
		final LevelAlertDecision unplugged = BatteryLevelReceiver.decideLevelAlert(
				notified, 100, NOT_CHARGING, FULL, UNPLUGGED, DEFAULTS);
		final LevelAlertDecision replugged = BatteryLevelReceiver.decideLevelAlert(
				unplugged.newState(), 100, NOT_CHARGING, FULL, PLUGGED, DEFAULTS);

		assertEquals(AlertType.FULL, replugged.notifyType());
		assertFalse(replugged.clearFullAlert());
	}

	// --- charger-disconnect reset semantics (via the pure state) ---------------------------------

	@Test
	public void restartMidEpisode_persistedStateSuppressesDuplicates() {
		// Process death loses nothing: the decision on the persisted state after a "restart" is the
		// same as it would have been in-process — no duplicate critical while still below threshold.
		final LevelAlertState persisted = new LevelAlertState(15, AlertType.CRITICAL, false);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				persisted, 13, DISCHARGING, NOT_FULL, UNPLUGGED, DEFAULTS);

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
