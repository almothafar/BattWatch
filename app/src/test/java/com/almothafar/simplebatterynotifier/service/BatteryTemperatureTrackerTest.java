package com.almothafar.simplebatterynotifier.service;

import android.os.BatteryManager;

import com.almothafar.simplebatterynotifier.service.BatteryTemperatureTracker.TemperatureRange;
import com.almothafar.simplebatterynotifier.service.BatteryTemperatureTracker.TemperatureStats;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the per-charge temperature range (issue #260). The pure helpers carry the feature's
 * correctness: the running min/max, the edge-triggered reset at the end of a charge with its drop-out
 * band, and the "device reported no temperature" gate. Temperatures are tenths of a degree Celsius
 * throughout.
 */
public class BatteryTemperatureTrackerTest {

	private static final int DISCHARGING = BatteryManager.BATTERY_STATUS_DISCHARGING;
	private static final int CHARGING = BatteryManager.BATTERY_STATUS_CHARGING;
	private static final int FULL = BatteryManager.BATTERY_STATUS_FULL;

	private static final TemperatureStats NOTHING_RECORDED = new TemperatureStats(0, 0, false, false);

	// --- the running range ---------------------------------------------------

	@Test
	public void firstReading_seedsBothEnds() {
		final TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 305, 80, DISCHARGING);

		assertEquals(305, stats.minTenthsC());
		assertEquals(305, stats.maxTenthsC());
		assertTrue(stats.hasData());
	}

	@Test
	public void hotterReading_widensTheMaximum() {
		final TemperatureStats seeded = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 305, 80, DISCHARGING);
		final TemperatureStats stats = BatteryTemperatureTracker.fold(seeded, 421, 78, DISCHARGING);

		assertEquals(305, stats.minTenthsC());
		assertEquals(421, stats.maxTenthsC());
	}

	@Test
	public void colderReading_widensTheMinimum() {
		final TemperatureStats seeded = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 305, 80, DISCHARGING);
		final TemperatureStats stats = BatteryTemperatureTracker.fold(seeded, 188, 78, DISCHARGING);

		assertEquals(188, stats.minTenthsC());
		assertEquals(305, stats.maxTenthsC());
	}

	@Test
	public void readingInsideTheRange_changesNothing() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 200, 80, DISCHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 400, 79, DISCHARGING);

		// An equal state back, so record()'s !equals check skips the write — the save-on-change rule
		// the trackers share.
		assertEquals(stats, BatteryTemperatureTracker.fold(stats, 300, 78, DISCHARGING));
	}

	@Test
	public void alarminglyHotReading_isRecordedNotDropped() {
		// 105 °C. "How hot did it actually get?" is the whole question this card answers, so the only
		// reading ever filtered out is the one that means "no reading" — nothing is second-guessed for
		// being extreme.
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 250, 80, DISCHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 1050, 78, DISCHARGING);

		assertEquals(1050, stats.maxTenthsC());
	}

	// --- the "no reading" gate -----------------------------------------------

	@Test
	public void unreportedReading_isIgnored() {
		// SystemService defaults EXTRA_TEMPERATURE to 0, so 0 means "this device didn't report one".
		assertEquals(NOTHING_RECORDED, BatteryTemperatureTracker.fold(NOTHING_RECORDED, 0, 80, DISCHARGING));
	}

	@Test
	public void unreportedReading_leavesAnExistingRangeUntouched() {
		final TemperatureStats seeded = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 305, 80, DISCHARGING);

		assertEquals(seeded, BatteryTemperatureTracker.fold(seeded, 0, 79, DISCHARGING));
	}

	@Test
	public void unreportedReading_stillMovesTheResetFlag() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 330, 100, FULL);
		assertTrue(stats.fullSeen());

		// The flag must not be gated behind a usable reading: on a device that never reports a
		// temperature it would freeze on whatever a fresh install left it holding.
		stats = BatteryTemperatureTracker.fold(stats, 0, 60, DISCHARGING);

		assertFalse(stats.fullSeen());
		assertEquals(330, stats.minTenthsC());
		assertEquals(330, stats.maxTenthsC());
	}

	@Test
	public void chargeCompletingWithNoReading_clearsTheRangeForTheNextOne() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 200, 60, DISCHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 450, 80, CHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 0, 100, FULL);

		// The charge finished, so the old range is stale even though this tick brought nothing to
		// replace it with.
		assertTrue(stats.isEmpty());
		assertTrue(stats.fullSeen());

		stats = BatteryTemperatureTracker.fold(stats, 330, 100, FULL);
		assertEquals(330, stats.minTenthsC());
		assertEquals(330, stats.maxTenthsC());
	}

	// --- the end-of-charge reset ---------------------------------------------

	@Test
	public void chargeCompleting_startsAFreshRangeAtThatReading() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 200, 60, CHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 450, 90, CHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 330, 100, FULL);

		assertEquals(330, stats.minTenthsC());
		assertEquals(330, stats.maxTenthsC());
		assertTrue(stats.fullSeen());
	}

	@Test
	public void stayingAtFull_doesNotKeepResettingTheRange() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 330, 100, FULL);
		// Still plugged in at full: these must widen the new range, not wipe it on every tick.
		stats = BatteryTemperatureTracker.fold(stats, 360, 100, FULL);
		stats = BatteryTemperatureTracker.fold(stats, 310, 100, FULL);

		assertEquals(310, stats.minTenthsC());
		assertEquals(360, stats.maxTenthsC());
	}

	@Test
	public void overnightTopUpCycle_doesNotResetTheRangeAgain() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 330, 100, FULL);

		// A phone left on the charger drifts 100 → 99 and tops back up, over and over. Re-arming on a
		// bare "not full" would wipe the range on each of those cycles, so by morning it would span a
		// few minutes instead of the night.
		stats = BatteryTemperatureTracker.fold(stats, 360, 99, CHARGING);
		assertTrue(stats.fullSeen());

		stats = BatteryTemperatureTracker.fold(stats, 310, 100, FULL);

		assertEquals(310, stats.minTenthsC());
		assertEquals(360, stats.maxTenthsC());
	}

	@Test
	public void leavingFull_reArmsOnlyOnceOutOfTheFullBand() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 330, 100, FULL);

		stats = BatteryTemperatureTracker.fold(stats, 340, 96, DISCHARGING);
		assertTrue(stats.fullSeen());

		stats = BatteryTemperatureTracker.fold(stats, 350, NotificationService.FULL_PERCENTAGE, DISCHARGING);
		assertFalse(stats.fullSeen());
	}

	@Test
	public void leavingFull_reArmsTheResetForTheNextCharge() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 330, 100, FULL);
		stats = BatteryTemperatureTracker.fold(stats, 420, 70, DISCHARGING);
		assertFalse(stats.fullSeen());

		stats = BatteryTemperatureTracker.fold(stats, 350, 100, FULL);
		assertEquals(350, stats.minTenthsC());
		assertEquals(350, stats.maxTenthsC());
	}

	@Test
	public void chargeCappedDeviceRestingAtFull_doesNotReArmOnItsOwnLevel() {
		// A device whose charge cap stops it at 80% reports FULL there. Its resting level is below the
		// drop-out band, so re-arming on the level alone would reset the range on every tick.
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 300, 80, FULL);
		stats = BatteryTemperatureTracker.fold(stats, 340, 80, FULL);
		stats = BatteryTemperatureTracker.fold(stats, 290, 80, FULL);

		assertEquals(290, stats.minTenthsC());
		assertEquals(340, stats.maxTenthsC());
	}

	@Test
	public void plugInAndUnplugAlone_doNotResetTheRange() {
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 250, 40, DISCHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 430, 55, CHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 300, 70, DISCHARGING);

		// The charge never completed, so the range still spans the whole stretch.
		assertEquals(250, stats.minTenthsC());
		assertEquals(430, stats.maxTenthsC());
	}

	@Test
	public void fullLevelWithoutFullStatus_countsAsComplete() {
		// Some OEMs report 100% a tick before the status catches up.
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 250, 90, CHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 400, 100, CHARGING);

		assertEquals(400, stats.minTenthsC());
		assertEquals(400, stats.maxTenthsC());
	}

	@Test
	public void fullStatusBelowFullLevel_countsAsComplete() {
		// A device with a charge cap reports FULL well short of 100%.
		TemperatureStats stats = BatteryTemperatureTracker.fold(NOTHING_RECORDED, 250, 70, CHARGING);
		stats = BatteryTemperatureTracker.fold(stats, 400, 80, FULL);

		assertEquals(400, stats.minTenthsC());
		assertEquals(400, stats.maxTenthsC());
	}

	@Test
	public void isChargeComplete_isFalseWhileStillCharging() {
		assertFalse(BatteryTemperatureTracker.isChargeComplete(99, CHARGING));
	}

	// --- the display view ----------------------------------------------------

	@Test
	public void summarize_isNullBeforeAnythingIsRecorded() {
		assertNull(BatteryTemperatureTracker.summarize(NOTHING_RECORDED));
	}

	@Test
	public void summarize_exposesTheRecordedRange() {
		final TemperatureStats stats = new TemperatureStats(188, 421, true, false);
		final TemperatureRange range = BatteryTemperatureTracker.summarize(stats);

		assertEquals(new TemperatureRange(188, 421), range);
	}
}
