package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.service.ScreenDrainTracker.ScreenState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static com.almothafar.simplebatterynotifier.service.ScreenDrainTracker.ScreenState.OFF;
import static com.almothafar.simplebatterynotifier.service.ScreenDrainTracker.ScreenState.ON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The screen-on / screen-off drain split (#193): a session accumulator that runs from the last unplug, attributing each stretch of time and level drop to
 * the screen state it was spent in. Driven through the tracker's clock-taking entry points — a battery broadcast, a screen broadcast, a monitoring start —
 * on the real transient preferences, so a test can say "an hour later, with the screen off" without waiting an hour.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScreenDrainTrackerTest {

	private static final long T0 = 1_700_000_000_000L;
	private static final long MINUTE = 60_000L;

	private Context context;
	private SharedPreferences prefs;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		prefs = TransientState.prefs(context);
		prefs.edit().clear().commit();
	}

	private static BatteryDO unplugged(int level) {
		return new BatteryDO().setLevel(level)
		                      .setScale(100)
		                      .setStatus(BatteryManager.BATTERY_STATUS_DISCHARGING)
		                      .setPlugged(0)
		                      .setCurrentMicroAmps(Integer.MIN_VALUE);
	}

	private static BatteryDO unplugged(int level, int currentMilliAmps) {
		return unplugged(level).setCurrentMicroAmps(currentMilliAmps * 1000);
	}

	/** A battery broadcast the monitoring service receives, and the split as it reads straight after. */
	private ScreenDrainTracker.ScreenSplit observe(long at, BatteryDO batteryDO, ScreenState screen) {
		ScreenDrainTracker.batteryChangedAt(prefs, at, batteryDO, screen);
		return read(batteryDO);
	}

	private ScreenDrainTracker.ScreenSplit read(BatteryDO batteryDO) {
		return ScreenDrainTracker.read(prefs, batteryDO);
	}

	@Test
	public void anHourWithTheScreenOff_givesTheScreenOffDrainRate() {
		observe(T0, unplugged(80), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 60 * MINUTE, unplugged(79), OFF);

		assertTrue("an hour off with a 1% drop is enough to show the screen-off rate", split.off().hasRate());
		assertEquals("1% over one hour is 1.0 %/h", 10, split.off().tenthsPercentPerHour());
	}

	@Test
	public void aScreenToggle_creditsTheStretchBeforeItToTheStateItWasSpentIn() {
		observe(T0, unplugged(80), ON);
		observe(T0 + 20 * MINUTE, unplugged(77), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 80 * MINUTE, unplugged(76), ON);

		assertEquals("3% in the 20 minutes on is 9.0 %/h", 90, split.on().tenthsPercentPerHour());
		assertEquals("1% in the hour off is 1.0 %/h", 10, split.off().tenthsPercentPerHour());
	}

	@Test
	public void eachState_warmsUpOnItsOwn() {
		observe(T0, unplugged(80), ON);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 15 * MINUTE, unplugged(78), OFF);
		final ScreenDrainTracker.ScreenSplit later = observe(T0 + 74 * MINUTE, unplugged(77), OFF);
		final ScreenDrainTracker.ScreenSplit warm = observe(T0 + 75 * MINUTE, unplugged(77), OFF);

		assertTrue("a quarter of an hour on is enough for the screen-on rate", split.on().hasRate());
		assertFalse("59 minutes off is not yet enough for the screen-off rate", later.off().hasRate());
		assertTrue("the screen-on rate stays while the screen-off one warms up", later.on().hasRate());
		assertTrue("an hour off is", warm.off().hasRate());
	}

	@Test
	public void screenOn_isNotReadyBeforeAQuarterOfAnHour() {
		observe(T0, unplugged(80), ON);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 14 * MINUTE, unplugged(78), OFF);

		assertFalse(split.on().hasRate());
	}

	@Test
	public void noLevelDrop_isStillCalculatingRatherThanZero() {
		observe(T0, unplugged(80), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 90 * MINUTE, unplugged(80), OFF);

		assertFalse("a level that hasn't moved has no rate to show yet", split.off().hasRate());
	}

	@Test
	public void pluggingIn_startsTheNextSessionFromNothing_evenWithoutACharge() {
		observe(T0, unplugged(80), OFF);
		observe(T0 + 120 * MINUTE, unplugged(78), OFF);
		// Plugged in for a minute and out again at the same level, so no rise in level can be what restarts the session.
		final BatteryDO charging = unplugged(78).setStatus(BatteryManager.BATTERY_STATUS_CHARGING).setPlugged(BatteryManager.BATTERY_PLUGGED_AC);
		final ScreenDrainTracker.ScreenSplit whilePlugged = observe(T0 + 121 * MINUTE, charging, OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 122 * MINUTE, unplugged(78), OFF);

		assertFalse("nothing shows while plugged in", whilePlugged.off().hasRate());
		assertFalse("the two hours before the plug-in belong to the last session", split.off().hasRate());
	}

	@Test
	public void readWhilePlugged_showsNothing() {
		observe(T0, unplugged(80), OFF);
		observe(T0 + 120 * MINUTE, unplugged(79), OFF);
		final BatteryDO plugged = unplugged(79).setPlugged(BatteryManager.BATTERY_PLUGGED_USB);

		assertFalse(read(plugged).off().hasRate());
	}

	@Test
	public void aChargeNobodySaw_startsANewSession() {
		observe(T0, unplugged(50), OFF);
		observe(T0 + 120 * MINUTE, unplugged(48), ON);
		// Monitoring was down while the phone charged; the next broadcast finds the level well above where the open stretch began.
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 300 * MINUTE, unplugged(90), ON);

		assertFalse("the two hours off before the charge belong to the last session", split.off().hasRate());
	}

	@Test
	public void aChargeNobodySaw_startsANewSession_whenMonitoringRestarts() {
		observe(T0, unplugged(50), OFF);
		observe(T0 + 120 * MINUTE, unplugged(48), ON);
		ScreenDrainTracker.monitoringStartedAt(prefs, T0 + 300 * MINUTE, unplugged(90), ON);

		assertFalse(read(unplugged(90)).off().hasRate());
	}

	@Test
	public void aOnePointRise_isTheGaugeWobbling_notACharge() {
		observe(T0, unplugged(80), ON);
		observe(T0 + 20 * MINUTE, unplugged(77), OFF);
		observe(T0 + 30 * MINUTE, unplugged(78), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 80 * MINUTE, unplugged(76), ON);

		assertEquals("the 20 minutes on are still there: 9.0 %/h", 90, split.on().tenthsPercentPerHour());
	}

	@Test
	public void currentReadings_closerThanTheSpacingCountOnce() {
		observe(T0, unplugged(80, -100), OFF);
		observe(T0 + 25_000, unplugged(80, -100), OFF);
		observe(T0 + 30_000, unplugged(80, -900), OFF);
		observe(T0 + 35_000, unplugged(80, -900), OFF);
		observe(T0 + 30 * MINUTE, unplugged(80, -100), OFF);
		observe(T0 + 60 * MINUTE, unplugged(79, -100), OFF);

		assertEquals("the two readings within 20 s of the one before are skipped", -100, read(unplugged(79)).off().avgCurrentMilliAmps());
	}

	@Test
	public void twoReadings_areNotAnAverageYet() {
		observe(T0, unplugged(80), OFF);
		observe(T0 + 30 * MINUTE, unplugged(80, -100), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 60 * MINUTE, unplugged(79, -100), OFF);

		assertTrue(split.off().hasRate());
		assertFalse("an average needs three readings, like the rate window's", split.off().hasCurrent());
	}

	@Test
	public void anAverageBelowTenMilliAmps_isHidden() {
		// Kirin reports CURRENT_NOW in mA, so a real 800 mA arrives as 800 µA and would read as a nonsense 1 mA (#152).
		observe(T0, unplugged(80), OFF);
		observe(T0 + 20 * MINUTE, unplugged(80).setCurrentMicroAmps(-800), OFF);
		observe(T0 + 40 * MINUTE, unplugged(80).setCurrentMicroAmps(-800), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 60 * MINUTE, unplugged(79).setCurrentMicroAmps(-800), OFF);

		assertFalse(split.off().hasCurrent());
	}

	@Test
	public void currentReadings_averageWithinTheirState_leavingOutTheUnplugAndTheToggle() {
		// The unplug's own broadcast can still carry the charger's current.
		observe(T0, unplugged(80, 1500), ON);
		observe(T0 + 2 * MINUTE, unplugged(80, -400), ON);
		observe(T0 + 5 * MINUTE, unplugged(80, -380), ON);
		observe(T0 + 10 * MINUTE, unplugged(79, -420), ON);
		// The sample that first sees the screen off, and one just after it, while the display is still powering down.
		observe(T0 + 15 * MINUTE, unplugged(78, -900), OFF);
		observe(T0 + 15 * MINUTE + 10_000, unplugged(78, -900), OFF);
		observe(T0 + 30 * MINUTE, unplugged(78, -60), OFF);
		observe(T0 + 45 * MINUTE, unplugged(78, -50), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 75 * MINUTE, unplugged(77, -70), OFF);

		assertTrue(split.on().hasCurrent());
		assertEquals("the three screen-on readings average to 400 mA", -400, split.on().avgCurrentMilliAmps());
		assertTrue(split.off().hasCurrent());
		assertEquals("the three screen-off readings after the toggle average to 60 mA", -60, split.off().avgCurrentMilliAmps());
	}

	@Test
	public void aScreenBroadcast_closesTheStretchAtTheToggle_andTakesNoReading() {
		observe(T0, unplugged(80, -400), ON);
		observe(T0 + 5 * MINUTE, unplugged(80, -400), ON);
		observe(T0 + 10 * MINUTE, unplugged(79, -400), ON);
		ScreenDrainTracker.screenChangedAt(prefs, T0 + 20 * MINUTE, unplugged(77, -900), OFF);
		// A late broadcast after the toggle was already seen: the same state, so nothing closes, and still no reading.
		ScreenDrainTracker.screenChangedAt(prefs, T0 + 25 * MINUTE, unplugged(77, -900), OFF);
		observe(T0 + 40 * MINUTE, unplugged(77, -60), OFF);
		observe(T0 + 60 * MINUTE, unplugged(77, -60), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 80 * MINUTE, unplugged(76, -60), OFF);

		assertEquals("the 20 minutes up to the broadcast are screen-on: 3% is 9.0 %/h", 90, split.on().tenthsPercentPerHour());
		assertEquals("neither broadcast's reading lands in the screen-off average", -60, split.off().avgCurrentMilliAmps());
	}

	@Test
	public void monitoringRestart_dropsTheStretchItCouldNotWatch() {
		observe(T0, unplugged(80), OFF);
		// Monitoring was down for five hours: the screen may have gone on and off in that time without anyone seeing it.
		ScreenDrainTracker.monitoringStartedAt(prefs, T0 + 300 * MINUTE, unplugged(70), ON);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 320 * MINUTE, unplugged(67), OFF);

		assertFalse("the five unwatched hours count towards neither state", split.off().hasRate());
		assertEquals("only the 20 watched minutes count: 3% in 20 min is 9.0 %/h", 90, split.on().tenthsPercentPerHour());
	}

	@Test
	public void monitoringRestart_keepsWhatWasWatchedBeforeIt() {
		// Screen off overnight from 95%, a broadcast at each hourly level tick; the system kills monitoring just after the sixth and restarts it.
		observe(T0, unplugged(95), OFF);
		for (int hour = 1; hour <= 6; hour++) {
			observe(T0 + hour * 60 * MINUTE, unplugged(95 - hour), OFF);
		}
		ScreenDrainTracker.monitoringStartedAt(prefs, T0 + 365 * MINUTE, unplugged(89), OFF);

		assertEquals("the six watched hours stay: 6% in 6 h is 1.0 %/h", 10, read(unplugged(89)).off().tenthsPercentPerHour());
	}

	@Test
	public void aClockSetBack_startsCountingAgainFromTheNewTime() {
		observe(T0, unplugged(80), OFF);
		observe(T0 - 30 * MINUTE, unplugged(80), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 60 * MINUTE, unplugged(79), OFF);

		assertEquals("the 90 minutes since the clock was set back count: 1% in 1.5 h is 0.7 %/h", 7, split.off().tenthsPercentPerHour());
	}

	@Test
	public void aClockSetBack_keepsTakingCurrentReadings() {
		observe(T0, unplugged(80, -100), OFF);
		observe(T0 - 30 * MINUTE, unplugged(80, -100), OFF);
		observe(T0 - 20 * MINUTE, unplugged(80, -100), OFF);
		final ScreenDrainTracker.ScreenSplit split = observe(T0 + 40 * MINUTE, unplugged(79, -100), OFF);

		assertTrue("the readings stamped before the start still count, so there are three", split.off().hasCurrent());
	}

	@Test
	@Config(qualifiers = "ar-rEG")
	public void theValue_keepsWesternDigits_onAnArabicPhone() {
		// Premise: this locale really does pick Eastern Arabic digits for a plain %d, so a locale-following format would fail here (#96, #273).
		assertEquals("٤٠", String.format(Locale.getDefault(), "%d", 40));

		final String value = ScreenDrainTracker.formatValue(context, new ScreenDrainTracker.ScreenDrain(true, -392, true, 104));
		assertTrue(value, value.contains("392") && value.contains("10.4"));
	}
}
