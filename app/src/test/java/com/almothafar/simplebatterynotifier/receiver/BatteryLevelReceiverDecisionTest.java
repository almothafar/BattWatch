package com.almothafar.simplebatterynotifier.receiver;

import android.content.SharedPreferences;

import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.ChargeState;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.LevelAlertConfig;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.LevelAlertDecision;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.LevelAlertState;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.TemperatureDecision;
import com.almothafar.simplebatterynotifier.service.AlertType;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.util.AppPrefs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
	private static final int TARGET_90 = 90;
	// The unplug reminder's clock (#264). NOW is an arbitrary fixed instant — nothing but the reminder reads it.
	private static final long NOW = 1_000_000L;
	private static final long REMINDER_GAP_MS = 15 * 60_000L;

	// Every pre-#263 case runs at the maximum charge target, where the alert waits for a genuinely complete charge — so this suite doubles as the proof that
	// setting the slider to 100% restores the pre-#263 behaviour exactly. It is deliberately NOT the shipped default: an install that never touches the slider
	// gets DEFAULT_CHARGE_TARGET (90) and is meant to start alerting there, which is the whole point of #263. The target-driven cases below cover that path.
	private static final LevelAlertConfig DEFAULTS =
			config(AppPrefs.MAX_CHARGE_TARGET, true, true, false);
	private static final LevelAlertConfig TARGET_AT_90 =
			config(TARGET_90, true, true, false);

	// The charge side of a broadcast. A completed charge reports BATTERY_STATUS_FULL rather than
	// CHARGING, so the full cases are "not charging" without being a discharge.
	private static final ChargeState DISCHARGING = new ChargeState(false, false, false, false);
	private static final ChargeState UNPLUGGED_STILL_FULL = new ChargeState(false, true, false, false);
	private static final ChargeState CHARGING = new ChargeState(true, false, false, true);
	private static final ChargeState FULL_ON_CHARGER = new ChargeState(false, true, false, true);
	// Plugged in but neither charging nor full: BATTERY_STATUS_NOT_CHARGING, what a device parked at an OEM charge cap reports. Distinguishes the charging gate
	// from the cable gate, which every other case here has moving together.
	private static final ChargeState PLUGGED_NOT_CHARGING = new ChargeState(false, false, true, true);

	// Episode state shorthands: nothing alerted yet, and "the full alert fired and is on screen".
	private static LevelAlertState fresh(int prevLevel, AlertType prevType) {
		return new LevelAlertState(prevLevel, prevType, false, false);
	}

	private static LevelAlertState fullAlertShowing(int prevLevel) {
		return new LevelAlertState(prevLevel, null, true, true);
	}

	/** The same, mid-charge with the reminder clock already running — the state a charge session is in between reminders (#264). */
	private static LevelAlertState fullAlertShowing(int prevLevel, long alertAt) {
		return new LevelAlertState(prevLevel, null, true, true, alertAt);
	}

	/**
	 * A config with the unplug reminder off, which is the shipped default and the premise of every case that isn't about the reminder. The two thresholds are
	 * fixed for the whole suite, so only what a case actually varies is named at the call site.
	 */
	private static LevelAlertConfig config(int chargeTarget, boolean warningEnabled, boolean fullNotifyEnabled, boolean alertEveryTick) {
		return new LevelAlertConfig(CRITICAL, WARNING, chargeTarget, warningEnabled, fullNotifyEnabled, alertEveryTick, false, REMINDER_GAP_MS);
	}

	/**
	 * A config with the unplug reminder on (#264), at the given charge target. Everything else matches {@link #config}.
	 */
	private static LevelAlertConfig remindingAt(int chargeTarget) {
		return new LevelAlertConfig(CRITICAL, WARNING, chargeTarget, true, true, false, true, REMINDER_GAP_MS);
	}

	/**
	 * The decision at a fixed instant. Only the unplug reminder (#264) reads the clock, so every other case can hold it still and the reminder cases pass their
	 * own {@code now} to the five-argument core directly.
	 */
	private static LevelAlertDecision decide(LevelAlertState state, int percentage, ChargeState power, LevelAlertConfig config) {
		return BatteryLevelReceiver.decideLevelAlert(state, percentage, power, config, NOW);
	}

	// --- discharging: critical/warning thresholds and de-dupe -----------------------------------

	@Test
	public void discharging_belowCritical_firesCriticalOnce() {
		final LevelAlertDecision first = decide(
				fresh(16, null), 15, DISCHARGING, DEFAULTS);

		assertEquals(AlertType.CRITICAL, first.notifyType());
		assertEquals(fresh(15, AlertType.CRITICAL), first.newState());

		// Next tick, still below critical: the persisted prevType suppresses the duplicate.
		final LevelAlertDecision second = decide(
				first.newState(), 14, DISCHARGING, DEFAULTS);
		assertNull(second.notifyType());
		assertEquals(14, second.newState().prevLevel());
	}

	@Test
	public void discharging_inWarningBand_firesWarningOnce() {
		final LevelAlertDecision first = decide(
				fresh(41, null), 38, DISCHARGING, DEFAULTS);

		assertEquals(AlertType.WARNING, first.notifyType());

		final LevelAlertDecision second = decide(
				first.newState(), 35, DISCHARGING, DEFAULTS);
		assertNull(second.notifyType());
	}

	@Test
	public void discharging_aboveWarning_noAlert() {
		final LevelAlertDecision d = decide(
				fresh(81, null), 80, DISCHARGING, DEFAULTS);

		assertNull(d.notifyType());
		assertEquals(80, d.newState().prevLevel());
	}

	@Test
	public void discharging_warningDisabled_staysSilentInWarningBand() {
		final LevelAlertConfig noWarning = config(AppPrefs.MAX_CHARGE_TARGET, false, true, false);
		final LevelAlertDecision d = decide(
				fresh(41, null), 38, DISCHARGING, noWarning);

		assertNull(d.notifyType());
	}

	@Test
	public void discharging_warningThenCritical_escalates() {
		final LevelAlertDecision d = decide(
				fresh(35, AlertType.WARNING), 20, DISCHARGING, DEFAULTS);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void discharging_alertEveryTick_repeatsCritical() {
		final LevelAlertConfig everyTick = config(AppPrefs.MAX_CHARGE_TARGET, true, true, true);
		final LevelAlertDecision d = decide(
				fresh(15, AlertType.CRITICAL), 14, DISCHARGING, everyTick);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void discharging_atRedAlertLevel_reFiresCriticalDespiteDeDupe() {
		// Already alerted critical this episode, but at/below the red-alert level it must re-fire.
		final LevelAlertDecision d = decide(
				fresh(5, AlertType.CRITICAL), NotificationService.RED_ALERT_LEVEL, DISCHARGING, DEFAULTS);

		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void unchangedLevel_skipsTheDischargeBranch() {
		// Same level as last tick routes to the charging-or-full branch (the receiver's historical
		// split), so a repeated broadcast at the same percentage can't duplicate a level alert.
		final LevelAlertDecision d = decide(
				fresh(15, AlertType.CRITICAL), 15, DISCHARGING, DEFAULTS);

		assertNull(d.notifyType());
	}

	// --- full-battery alert: once per charge session, re-armed on the way down -------------------

	@Test
	public void charging_full_firesOnceThenHolds() {
		final LevelAlertDecision first = decide(
				fresh(100, null), 100, FULL_ON_CHARGER, DEFAULTS);

		assertEquals(AlertType.FULL, first.notifyType());
		assertTrue(first.newState().fullNotified());
		assertTrue(first.newState().fullAlertShown());

		final LevelAlertDecision second = decide(
				first.newState(), 100, FULL_ON_CHARGER, DEFAULTS);
		assertNull(second.notifyType());
	}

	@Test
	public void charging_fullDisabled_staysSilent() {
		final LevelAlertConfig noFull = config(AppPrefs.MAX_CHARGE_TARGET, true, false, false);
		final LevelAlertDecision d = decide(
				fresh(100, null), 100, FULL_ON_CHARGER, noFull);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
		assertFalse(d.newState().fullAlertShown());
	}

	@Test
	public void charging_levelLeavesFullBand_reArmsFullAlert() {
		// Notified at full, then the level drops to 90 (≤ the 95 re-arm level of a 100 target, above warning): re-armed.
		final LevelAlertDecision d = decide(
				fullAlertShowing(90), 90, CHARGING, DEFAULTS);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void charging_belowWarningBand_doesNotReArmFullAlert() {
		// The re-arm band is (warning, target − margin]: charging low keeps the flag as-is.
		final LevelAlertDecision d = decide(
				fullAlertShowing(30), 30, CHARGING, DEFAULTS);

		assertTrue(d.newState().fullNotified());
	}

	@Test
	public void charging_reArmBand_doesNotForgetTheAlertIsStillOnScreen() {
		// The band re-arms the once-per-charge flag, but the notification is still up — conflating the
		// two is what left a stale full alert undismissable after a missed unplug.
		final LevelAlertDecision reArmed = decide(
				fullAlertShowing(95), 95, CHARGING, DEFAULTS);

		assertFalse(reArmed.newState().fullNotified());
		assertTrue(reArmed.newState().fullAlertShown());

		// Unplugged in that window, the alert is still dismissed.
		final LevelAlertDecision unplugged = decide(
				reArmed.newState(), 95, DISCHARGING, DEFAULTS);
		assertTrue(unplugged.clearFullAlert());
		assertFalse(unplugged.newState().fullAlertShown());
	}

	// --- the charge target (#263) ------------------------------------------------------------------

	@Test
	public void charging_reachesTheTarget_firesBeforeAFullCharge() {
		// The point of the feature: at a target of 90 the alert arrives while the battery is still climbing, not once it has already sat at 100%.
		final LevelAlertDecision d = decide(
				fresh(89, null), TARGET_90, CHARGING, TARGET_AT_90);

		assertEquals(AlertType.FULL, d.notifyType());
		assertTrue(d.newState().fullNotified());
		assertTrue(d.newState().fullAlertShown());
	}

	@Test
	public void charging_belowTheTarget_staysSilent() {
		final LevelAlertDecision d = decide(
				fresh(88, null), 89, CHARGING, TARGET_AT_90);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void charging_fromTheTargetToFull_firesExactlyOnce() {
		// The re-arm level moves with the target for this reason alone: at a fixed 95 the flag cleared on the very tick the alert fired, and every percent from
		// 90 to 96 fired again — seven notifications for one charge.
		LevelAlertState state = fresh(89, null);
		int fired = 0;

		for (int percentage = TARGET_90; percentage <= 100; percentage++) {
			final LevelAlertDecision d = decide(state, percentage, CHARGING, TARGET_AT_90);
			state = d.newState();
			if (d.notifyType() == AlertType.FULL) {
				fired++;
			}
		}
		assertEquals(1, fired);

		// And the completed charge doesn't add a second one on top.
		assertNull(decide(state, 100, FULL_ON_CHARGER, TARGET_AT_90).notifyType());
	}

	@Test
	public void discharging_pastTheTarget_neverFires() {
		// decideChargingOrFull is also reached with the level unchanged while discharging, so the trigger has to be gated on charging: sitting at 90% on the
		// way DOWN is not a finished charge.
		final LevelAlertDecision changed = decide(
				fresh(91, null), TARGET_90, DISCHARGING, TARGET_AT_90);
		assertNull(changed.notifyType());

		final LevelAlertDecision unchanged = decide(
				fresh(TARGET_90, null), TARGET_90, DISCHARGING, TARGET_AT_90);
		assertNull(unchanged.notifyType());
		assertFalse(unchanged.newState().fullNotified());
	}

	@Test
	public void pluggedAndHoldingAtACap_firesOnTheTarget() {
		// A device parked at its own charge cap reports NOT_CHARGING, not FULL: cable in, nothing flowing, no completed-charge status ever coming. Requiring
		// CHARGING left the whole feature silently dead there — on exactly the phones whose owners went looking for a charge target.
		final LevelAlertDecision d = decide(
				fresh(95, null), 95, PLUGGED_NOT_CHARGING, TARGET_AT_90);

		assertEquals(AlertType.FULL, d.notifyType());
		assertTrue(d.newState().fullNotified());
	}

	@Test
	public void pluggedAndHoldingAtACap_doesNotRepeatWhileItSitsThere() {
		// The cap holds the level steady for hours, so this broadcast repeats unchanged. Only the first may alert — the re-arm has to see the charge left
		// behind, not just a level inside the band.
		LevelAlertState state = fresh(95, null);
		int fired = 0;

		for (int tick = 0; tick < 5; tick++) {
			final LevelAlertDecision d = decide(state, 95, PLUGGED_NOT_CHARGING, TARGET_AT_90);
			state = d.newState();
			if (d.notifyType() == AlertType.FULL) {
				fired++;
			}
		}
		assertEquals(1, fired);
	}

	@Test
	public void pluggedButDischarging_neverFiresOnTheLevel() {
		// The cable alone is not enough: a phone drawing more than the charger supplies reports DISCHARGING with the cable in. The battery is going down, so no
		// charge is finishing.
		final ChargeState pluggedButDraining = new ChargeState(false, false, false, true);
		final LevelAlertDecision d = decide(
				fresh(95, null), 95, pluggedButDraining, TARGET_AT_90);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void pluggedInAboveTheTarget_firesTheAlert() {
		// Connecting a charger at 95% with a target of 90 has nothing left to reach, so the alert has to come from the level already being there. It rides on
		// the unplug re-arm, which is why removing that would break this quietly.
		final LevelAlertDecision unplugged = decide(
				fullAlertShowing(95), 95, DISCHARGING, TARGET_AT_90);
		assertFalse(unplugged.newState().fullNotified());

		final LevelAlertDecision replugged = decide(
				unplugged.newState(), 95, CHARGING, TARGET_AT_90);
		assertEquals(AlertType.FULL, replugged.notifyType());
	}

	@Test
	public void charging_droppingBelowTheReArmLevel_armsTheNextAlert() {
		// A top-up without unplugging: the level has to fall a margin below the target before the alert can fire again, so the flag holds at 86 and clears at
		// 85.
		final LevelAlertDecision inBand = decide(
				fullAlertShowing(86), 86, CHARGING, TARGET_AT_90);
		assertTrue(inBand.newState().fullNotified());

		final LevelAlertDecision dropped = decide(
				fullAlertShowing(85), 85, CHARGING, TARGET_AT_90);
		assertFalse(dropped.newState().fullNotified());
	}

	@Test
	public void maximumTarget_waitsForACompletedCharge() {
		// 100% is the opt-out: reaching the level is not enough, the battery must report the charge finished — bit for bit what the app did before the target
		// existed.
		final LevelAlertDecision atLevel = decide(
				fresh(99, null), 100, CHARGING, DEFAULTS);
		assertNull(atLevel.notifyType());

		final LevelAlertDecision complete = decide(
				atLevel.newState(), 100, FULL_ON_CHARGER, DEFAULTS);
		assertEquals(AlertType.FULL, complete.notifyType());
	}

	@Test
	public void belowTheTarget_aCompletedChargeStillFires() {
		// A charge-capped device reports FULL well short of the target. That is still a finished charge.
		final LevelAlertDecision d = decide(
				fresh(80, null), 80, FULL_ON_CHARGER, TARGET_AT_90);

		assertEquals(AlertType.FULL, d.notifyType());

		// ...and having fired, it must stay fired. 80 is inside the re-arm band of a 90 target, so without the "a tick that fired never re-arms" guard the flag
		// cleared on this very tick and the next identical broadcast alerted again — for as long as the phone sat on the charger.
		assertTrue(d.newState().fullNotified());
	}

	@Test
	public void chargeCappedBelowTheReArmLevel_doesNotRepeatOnEveryBroadcast() {
		// The user-visible half of the assertion above: a device parked at its 80% cap re-decides the same broadcast every few seconds, and only the first one
		// may alert.
		LevelAlertState state = fresh(80, null);
		int fired = 0;

		for (int tick = 0; tick < 5; tick++) {
			final LevelAlertDecision d = decide(state, 80, FULL_ON_CHARGER, TARGET_AT_90);
			state = d.newState();
			if (d.notifyType() == AlertType.FULL) {
				fired++;
			}
		}
		assertEquals(1, fired);
	}

	// --- the unplug reminder: the opt-in repeat on top of the once-per-charge alert (#264) --------

	@Test
	public void unplugReminder_offByDefault_leavesTheAlertAtOncePerCharge() {
		// The shipped default. Hours past any plausible gap, a battery sitting on the charger at the target still gets exactly the one alert.
		final LevelAlertDecision fired = decide(fresh(89, null), 90, CHARGING, TARGET_AT_90);
		assertEquals(AlertType.FULL, fired.notifyType());

		final LevelAlertDecision later = BatteryLevelReceiver.decideLevelAlert(
				fired.newState(), 90, CHARGING, TARGET_AT_90, NOW + 6 * REMINDER_GAP_MS);
		assertNull(later.notifyType());
	}

	@Test
	public void firstAlertOfTheCharge_isNotAReminder_andStartsTheClock() {
		// The distinction the dispatcher acts on: the first alert of a charge session posts normally, and it is what the reminder gap is then measured from.
		final LevelAlertDecision d = decide(fresh(89, null), 90, CHARGING, remindingAt(TARGET_90));

		assertEquals(AlertType.FULL, d.notifyType());
		assertFalse(d.reminder());
		assertEquals(NOW, d.newState().fullAlertAt());
	}

	@Test
	public void unplugReminder_rePostsTheAlertOnceTheGapElapses() {
		final LevelAlertDecision fired = decide(fresh(89, null), 90, CHARGING, remindingAt(TARGET_90));

		final LevelAlertDecision reminded = BatteryLevelReceiver.decideLevelAlert(
				fired.newState(), 91, CHARGING, remindingAt(TARGET_90), NOW + REMINDER_GAP_MS);

		assertEquals(AlertType.FULL, reminded.notifyType());
		assertTrue(reminded.reminder());
		// Still one episode, not a re-armed one — the repeat rides on the once-per-charge flag rather than replacing it.
		assertTrue(reminded.newState().fullNotified());
		assertTrue(reminded.newState().fullAlertShown());
	}

	@Test
	public void unplugReminder_staysQuietUntilTheGapHasFullyElapsed() {
		final LevelAlertDecision fired = decide(fresh(89, null), 90, CHARGING, remindingAt(TARGET_90));

		final LevelAlertDecision early = BatteryLevelReceiver.decideLevelAlert(
				fired.newState(), 90, CHARGING, remindingAt(TARGET_90), NOW + REMINDER_GAP_MS - 1);

		assertNull(early.notifyType());
		// The clock is left alone by a tick that didn't remind, so the next one is still due on the original schedule.
		assertEquals(NOW, early.newState().fullAlertAt());
	}

	@Test
	public void unplugReminder_spacesRepeatsFromTheLastPost() {
		// Measured from the last post, not the first: otherwise every broadcast after the first gap is "overdue" and the reminder becomes a per-tick alarm.
		final LevelAlertDecision fired = decide(fresh(89, null), 90, CHARGING, remindingAt(TARGET_90));
		final LevelAlertDecision first = BatteryLevelReceiver.decideLevelAlert(
				fired.newState(), 90, CHARGING, remindingAt(TARGET_90), NOW + REMINDER_GAP_MS);
		assertTrue(first.reminder());

		final LevelAlertDecision tooSoon = BatteryLevelReceiver.decideLevelAlert(
				first.newState(), 90, CHARGING, remindingAt(TARGET_90), NOW + REMINDER_GAP_MS + 1);
		assertNull(tooSoon.notifyType());

		final LevelAlertDecision second = BatteryLevelReceiver.decideLevelAlert(
				first.newState(), 90, CHARGING, remindingAt(TARGET_90), NOW + 2 * REMINDER_GAP_MS);
		assertTrue(second.reminder());
	}

	@Test
	public void unplugReminder_keepsGoingAtAnOemChargeCap() {
		// The device holding at its own cap reports NOT_CHARGING with the cable in and never reports FULL — the case a "still charging" reading of the feature
		// would go silent on, and the one whose owners asked for the reminder.
		final LevelAlertDecision fired = decide(fullAlertShowing(88, NOW), 88, PLUGGED_NOT_CHARGING, remindingAt(85));

		assertNull(fired.notifyType()); // not yet due

		final LevelAlertDecision reminded = BatteryLevelReceiver.decideLevelAlert(
				fired.newState(), 88, PLUGGED_NOT_CHARGING, remindingAt(85), NOW + REMINDER_GAP_MS);
		assertTrue(reminded.reminder());
	}

	@Test
	public void unplugReminder_stopsWhenTheChargerComesOut() {
		// "Until I unplug" is the whole promise: off the cable the episode is over, the shown alert is dismissed, and no reminder rides along behind it.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(90, NOW), 90, DISCHARGING, remindingAt(TARGET_90), NOW + 10 * REMINDER_GAP_MS);

		assertNull(d.notifyType());
		assertTrue(d.clearFullAlert());
		assertFalse(d.newState().fullNotified());
		assertEquals(0, d.newState().fullAlertAt());
	}

	@Test
	public void unplugReminder_stopsOnceTheLevelFallsBackBelowTheTarget() {
		// A phone drawing more than the cable supplies slides back out of "the charge you asked for is done", so there is nothing left to be reminded about.
		// The repeated level is what reaches the full-battery branch here, the same path chargeTarget_whileDischarging_neverAlerts uses.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(84, NOW), 84, PLUGGED_NOT_CHARGING, remindingAt(TARGET_90), NOW + 10 * REMINDER_GAP_MS);

		assertNull(d.notifyType());
		// Re-armed by the band, and the reminder clock re-arms with it rather than being left to fire the instant the next charge reaches the target.
		assertFalse(d.newState().fullNotified());
		assertEquals(0, d.newState().fullAlertAt());
	}

	@Test
	public void unplugReminder_withNoRecordedAlertTime_neverFires() {
		// What an install upgrading mid-charge reads back: the flag says an alert already fired, with no record of when. Treating that absent time as "long
		// ago" would greet exactly those users with a reminder the instant they updated.
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(90), 90, CHARGING, remindingAt(TARGET_90), NOW + 100 * REMINDER_GAP_MS);

		assertNull(d.notifyType());
	}

	@Test
	public void unplugReminder_neverOutlivesTheChargeSessionItStarted() {
		// Unplug, then plug back in above the target: that is a new session, so its first alert is a first alert — not a reminder inherited from the last one.
		final LevelAlertDecision unplugged = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(95, NOW), 95, DISCHARGING, remindingAt(TARGET_90), NOW + REMINDER_GAP_MS);
		assertFalse(unplugged.newState().fullNotified());

		final LevelAlertDecision replugged = BatteryLevelReceiver.decideLevelAlert(
				unplugged.newState(), 95, CHARGING, remindingAt(TARGET_90), NOW + 2 * REMINDER_GAP_MS);
		assertEquals(AlertType.FULL, replugged.notifyType());
		assertFalse(replugged.reminder());
	}

	@Test
	public void unplugReminder_withTheFullAlertSwitchedOff_saysNothing() {
		// The reminder repeats the full-battery alert; with that alert off there is nothing to repeat, whatever this switch says.
		final LevelAlertConfig noFullButReminding =
				new LevelAlertConfig(CRITICAL, WARNING, TARGET_90, true, false, false, true, REMINDER_GAP_MS);
		final LevelAlertDecision d = BatteryLevelReceiver.decideLevelAlert(
				fullAlertShowing(90, NOW), 90, CHARGING, noFullButReminding, NOW + 10 * REMINDER_GAP_MS);

		assertNull(d.notifyType());
	}

	// --- unplugged: the full alert is bounded by the charger, not by the status --------------------

	@Test
	public void unplugged_stillReportingFull_neverFires() {
		// The status stays BATTERY_STATUS_FULL at 100% on plenty of devices with the cable already out.
		// Firing here is what made the alert look like it refused to go away: the unplug handler cleared
		// it and the very next broadcast posted it straight back.
		final LevelAlertDecision d = decide(
				fresh(100, null), 100, UNPLUGGED_STILL_FULL, DEFAULTS);

		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
		assertFalse(d.clearFullAlert());
	}

	@Test
	public void unplugged_withFullAlertShown_dismissesItOnce() {
		// Missed unplug broadcast (process killed mid-charge): the shown alert is dismissed from
		// EXTRA_PLUGGED instead, then the session is re-armed so nothing dismisses twice.
		final LevelAlertDecision d = decide(
				fullAlertShowing(100), 100, UNPLUGGED_STILL_FULL, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertNull(d.notifyType());
		assertFalse(d.newState().fullNotified());
		assertFalse(d.newState().fullAlertShown());

		final LevelAlertDecision next = decide(
				d.newState(), 99, UNPLUGGED_STILL_FULL, DEFAULTS);
		assertFalse(next.clearFullAlert());
		assertNull(next.notifyType());
	}

	@Test
	public void unplugged_levelAlreadyDropping_stillDismissesTheFullAlert() {
		// The discharge branch (changed level) must clear it too — otherwise a restart that first sees
		// 99% leaves the alert up until the next charge session.
		final LevelAlertDecision d = decide(
				fullAlertShowing(100), 98, DISCHARGING, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertFalse(d.newState().fullNotified());
	}

	@Test
	public void unplugged_dischargedToCritical_stillAlertsWhileDismissingTheStaleFull() {
		// Both at once: the receiver dismisses first and posts after, so the critical alert survives
		// (they share one notification ID). BatteryLevelReceiverTest pins that ordering.
		final LevelAlertDecision d = decide(
				fullAlertShowing(21), 19, DISCHARGING, DEFAULTS);

		assertTrue(d.clearFullAlert());
		assertEquals(AlertType.CRITICAL, d.notifyType());
	}

	@Test
	public void unplugged_missedTransition_reArmsTheLevelDeDupeToo() {
		// The state-driven fallback must land in exactly the state onChargerDisconnected produces.
		// Re-arming only the full flag left prevType armed, silencing the next session's critical alert.
		final LevelAlertState afterFullCharge = new LevelAlertState(100, AlertType.CRITICAL, true, true);
		final LevelAlertDecision d = decide(
				afterFullCharge, 100, UNPLUGGED_STILL_FULL, DEFAULTS);

		assertNull(d.newState().prevType());
		assertEquals(new LevelAlertState(100, null, false, false), d.newState());
	}

	@Test
	public void unplugged_missedTransition_criticalStillAlertsWithWarningsOff() {
		// The user-visible consequence of the re-arm above: with warning alerts disabled nothing else
		// resets prevType on the way down, so a half re-arm stayed silent all the way to RED_ALERT_LEVEL.
		final LevelAlertConfig noWarning = config(AppPrefs.MAX_CHARGE_TARGET, false, true, false);
		final LevelAlertState afterFullCharge = new LevelAlertState(100, AlertType.CRITICAL, true, true);

		LevelAlertState state = decide(
				afterFullCharge, 100, UNPLUGGED_STILL_FULL, noWarning).newState();

		AlertType fired = null;
		for (int percentage = 99; percentage >= CRITICAL; percentage--) {
			final LevelAlertDecision d = decide(state, percentage, DISCHARGING, noWarning);
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
		final LevelAlertDecision d = decide(
				criticalOnScreen, 18, DISCHARGING, DEFAULTS);

		assertFalse(d.clearFullAlert());
	}

	@Test
	public void replugged_afterUnplugAtFull_firesAgain() {
		// The unplug re-arm is what lets a charger connected at 100% alert again, so it must survive
		// the dismissal path above.
		final LevelAlertDecision unplugged = decide(
				fullAlertShowing(100), 100, UNPLUGGED_STILL_FULL, DEFAULTS);
		final LevelAlertDecision replugged = decide(
				unplugged.newState(), 100, FULL_ON_CHARGER, DEFAULTS);

		assertEquals(AlertType.FULL, replugged.notifyType());
		assertFalse(replugged.clearFullAlert());
	}

	// --- charger-disconnect reset semantics (via the pure state) ---------------------------------

	@Test
	public void restartMidEpisode_persistedStateSuppressesDuplicates() {
		// Process death loses nothing: the decision on the persisted state after a "restart" is the
		// same as it would have been in-process — no duplicate critical while still below threshold.
		final LevelAlertDecision d = decide(
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

	// --- durability of the "alert is showing" flag (#268) -------------------------------------------
	// A real SharedPreferences can't tell commit() from apply() — both read back — so these assert on
	// the editor itself. That is the whole behaviour under test: the flag must reach disk before the
	// notification posts, or a process kill strands an alert nothing can dismiss.

	@Test
	public void temperatureFlag_whenTheAlertFires_isWrittenSynchronously() {
		final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
		final SharedPreferences prefs = mock(SharedPreferences.class);
		when(prefs.edit()).thenReturn(editor);

		BatteryLevelReceiver.persistTemperatureAlerted(prefs, true);

		verify(editor).commit();
		verify(editor, never()).apply();
	}

	@Test
	public void levelState_whenTheFullAlertReachesTheScreen_isWrittenSynchronously() {
		final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
		final SharedPreferences prefs = mock(SharedPreferences.class);
		when(prefs.edit()).thenReturn(editor);

		// The dismissal in decideLevelAlert keys on fullAlertShown, so losing this write leaves the
		// "Battery fully charged" notification up with nothing able to take it down.
		BatteryLevelReceiver.saveLevelStateIfChanged(prefs,
				new LevelAlertState(100, null, false, false),
				new LevelAlertState(100, null, true, true));

		verify(editor).commit();
		verify(editor, never()).apply();
	}

	@Test
	public void levelState_whenTheEpisodeReArms_staysAsynchronous() {
		final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
		final SharedPreferences prefs = mock(SharedPreferences.class);
		when(prefs.edit()).thenReturn(editor);

		BatteryLevelReceiver.saveLevelStateIfChanged(prefs,
				new LevelAlertState(100, null, true, true),
				new LevelAlertState(100, null, false, false));

		verify(editor).apply();
		verify(editor, never()).commit();
	}

	@Test
	public void levelState_unchanged_isNotWrittenAtAll() {
		// The churn guard: ACTION_BATTERY_CHANGED fires every few seconds and re-decides an identical
		// state, so the common tick must not touch the preferences file at all.
		final SharedPreferences prefs = mock(SharedPreferences.class);
		final LevelAlertState same = new LevelAlertState(100, null, true, true);

		BatteryLevelReceiver.saveLevelStateIfChanged(prefs, same, same);

		verify(prefs, never()).edit();
	}

	@Test
	public void temperatureFlag_whenTheSpellEnds_staysAsynchronous() {
		final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
		final SharedPreferences prefs = mock(SharedPreferences.class);
		when(prefs.edit()).thenReturn(editor);

		// Losing the re-arm write is harmless — the next cool tick re-decides it — so it must not pay
		// for a synchronous disk write.
		BatteryLevelReceiver.persistTemperatureAlerted(prefs, false);

		verify(editor).apply();
		verify(editor, never()).commit();
	}
}
