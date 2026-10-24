package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.service.RateWindowTestSupport;
import com.almothafar.simplebatterynotifier.service.ScreenDrainTestSupport;
import com.almothafar.simplebatterynotifier.service.SystemService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

/**
 * The details table's screen-on and screen-off drain rows (#193), and the time remaining they bracket (#354). Like the other live rows (#188), they are
 * always there on battery: a state still gathering time shows the small "calculating" hint with an info icon instead of the row appearing later and pushing
 * the table down. On the charger there is no split to show, so neither row is there.
 * <p>
 * The table only reads the split; the monitoring service's receivers are what feed it. Robolectric leaves the wall clock real for app code, so the past
 * stretches a test needs are recorded through {@link ScreenDrainTestSupport} at times of its choosing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BatteryDetailsScreenDrainRowsTest {

	private static final long MINUTE = 60_000L;

	private Context context;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
	}

	@Test
	public void whileDischarging_bothRowsArePresent_withTheCalculatingHint() {
		publishBattery(80, false);
		final TableLayout table = showDetails();

		for (final int labelRes : new int[]{R.string.screen_on_drain, R.string.screen_off_drain}) {
			final TextView value = valueOf(table, labelRes);
			assertNotNull(context.getString(labelRes) + " row is missing", value);
			assertEquals(context.getString(R.string.battery_value_calculating), value.getText().toString());
			assertEquals("a calculating row has the info icon, read after its words", infoDescription(context.getString(R.string.battery_value_calculating)),
					value.getContentDescription());
		}
	}

	@Test
	public void aStateThatHasRunLongEnough_showsItsDrain_whileTheOtherStillCalculates() {
		// Monitoring saw the screen go off two hours ago at 80%, and come back on a minute ago at 78%.
		final long now = System.currentTimeMillis();
		publishBattery(80, false);
		ScreenDrainTestSupport.recordAt(context, now - 120 * MINUTE, SystemService.getBatteryInfo(context), false);
		publishBattery(78, false);
		ScreenDrainTestSupport.recordAt(context, now - MINUTE, SystemService.getBatteryInfo(context), true);
		setScreenOn(true);
		final TableLayout table = showDetails();

		assertEquals("2% in 119 minutes off; 78% lasts 78 h", "1.0%/\u2060h · ~78h\u00A00m", valueOf(table, R.string.screen_off_drain).getText().toString());
		assertNull("a ready row has no info icon", valueOf(table, R.string.screen_off_drain).getContentDescription());
		assertEquals(context.getString(R.string.battery_value_calculating), valueOf(table, R.string.screen_on_drain).getText().toString());
	}

	@Test
	public void theTimeRemaining_projectsFromTheWholeTimeOffTheCharger_andFallsBetweenTheTwoStates() {
		// Monitoring saw an hour on from 90% to 80%, then 119 minutes off down to 78%: 12% in 179 minutes is 4.0 %/h overall.
		final long now = System.currentTimeMillis();
		publishBattery(90, false);
		ScreenDrainTestSupport.recordAt(context, now - 180 * MINUTE, SystemService.getBatteryInfo(context), true);
		publishBattery(80, false);
		ScreenDrainTestSupport.recordAt(context, now - 120 * MINUTE, SystemService.getBatteryInfo(context), false);
		publishBattery(78, false);
		ScreenDrainTestSupport.recordAt(context, now - MINUTE, SystemService.getBatteryInfo(context), true);
		setScreenOn(true);
		final TableLayout table = showDetails();

		// Nowhere to break inside a figure (a no-break space, a word joiner after the slash), so a row too wide for a phone wraps between figures.
		assertEquals("78% at 10.0 %/h lasts 468 min", "10.0%/\u2060h · ~7h\u00A048m", valueOf(table, R.string.screen_on_drain).getText().toString());
		assertEquals("78% at 1.0 %/h lasts 78 h", "1.0%/\u2060h · ~78h\u00A00m", valueOf(table, R.string.screen_off_drain).getText().toString());
		assertEquals("78% at 4.0 %/h lasts 1170 min", "~19h 30m", valueOf(table, R.string.time_remaining).getText().toString());
		assertEquals("on battery the time keeps its info icon, which says why it sits between the two", infoDescription("~19h 30m"),
				valueOf(table, R.string.time_remaining).getContentDescription());
	}

	@Test
	public void theTimeRemaining_atZero_readsTheDash_whenOnlyTheSessionRateIsIn() {
		// The overall drain is in and the window has no rate: with nothing left to time, the row says none is coming rather than "calculating".
		final long now = System.currentTimeMillis();
		publishBattery(2, false);
		ScreenDrainTestSupport.recordAt(context, now - 120 * MINUTE, SystemService.getBatteryInfo(context), false);
		publishBattery(0, false);
		ScreenDrainTestSupport.recordAt(context, now - MINUTE, SystemService.getBatteryInfo(context), true);
		setScreenOn(true);
		final TableLayout table = showDetails();

		assertEquals(context.getString(R.string.battery_value_pending), valueOf(table, R.string.time_remaining).getText().toString());
	}

	@Test
	public void theTimeToFull_keepsNoInfoIcon_onceItShowsATime() {
		// Charging is untouched: the info icon still marks only a value that isn't ready. The table's own reading stretches the filled window to seven
		// minutes, as above, so the rate is 17 %/h, and 38 points at that is 134 min.
		publishBattery(62, true);
		RateWindowTestSupport.fill(context, true, 60, 62);
		final TableLayout table = showDetails();

		assertEquals("17%/h", valueOf(table, R.string.charge_rate).getText().toString());
		assertEquals("~2h 14m", valueOf(table, R.string.time_to_full).getText().toString());
		assertNull(valueOf(table, R.string.time_to_full).getContentDescription());
	}

	@Test
	public void theTimeRemaining_comesFromTheDrainRate_untilTheSessionHasRunAnHour() {
		// Half an hour off the charger with the screen on: the screen-on drain is in, the overall drain is not, and the 10-minute drain rate stands in. The
		// table records its own reading as it opens, which stretches the filled window to seven minutes: 2% in 7 minutes is 17 %/h.
		final long now = System.currentTimeMillis();
		publishBattery(80, false);
		ScreenDrainTestSupport.recordAt(context, now - 30 * MINUTE, SystemService.getBatteryInfo(context), true);
		publishBattery(78, false);
		ScreenDrainTestSupport.recordAt(context, now - MINUTE, SystemService.getBatteryInfo(context), true);
		RateWindowTestSupport.fill(context, false, 80, 78);
		setScreenOn(true);
		final TableLayout table = showDetails();

		assertEquals("17%/h", valueOf(table, R.string.drain_rate).getText().toString());
		assertEquals("78% at 17 %/h lasts 275 min", "~4h 35m", valueOf(table, R.string.time_remaining).getText().toString());
	}

	@Test
	public void openingTheTable_creditsNothingThatMonitoringDidNotWatch() {
		// Monitoring saw the screen go off two hours ago at 80%, then the system stopped it. The phone was used unseen down to 60%, and now the app opens
		// before the service is back: the table must not credit those two hours to the screen-off drain.
		publishBattery(80, false);
		ScreenDrainTestSupport.recordAt(context, System.currentTimeMillis() - 120 * MINUTE, SystemService.getBatteryInfo(context), false);
		publishBattery(60, false);
		setScreenOn(true);
		final TableLayout table = showDetails();

		assertEquals(context.getString(R.string.battery_value_calculating), valueOf(table, R.string.screen_off_drain).getText().toString());
	}

	@Test
	public void whileCharging_neitherRowIsThere() {
		publishBattery(80, true);
		final TableLayout table = showDetails();

		assertNull(valueOf(table, R.string.screen_on_drain));
		assertNull(valueOf(table, R.string.screen_off_drain));
		assertNotNull("the table itself is there", valueOf(table, R.string.charge_rate));
	}

	@Test
	public void heldOnTheChargerAtACap_neitherRowIsThere() {
		// A charge cap reports NOT_CHARGING while plugged in, which the rate rows read as discharging. The split is not kept on the charger, so two rows
		// here could never fill.
		publishBattery(80, BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC);
		final TableLayout table = showDetails();

		assertNotNull("the rate rows read this as discharging", valueOf(table, R.string.drain_rate));
		assertNull(valueOf(table, R.string.screen_on_drain));
		assertNull(valueOf(table, R.string.screen_off_drain));
	}

	// --- Helpers ----------------------------------------------------------------------------------

	private void publishBattery(int level, boolean charging) {
		final int status = charging ? BatteryManager.BATTERY_STATUS_CHARGING : BatteryManager.BATTERY_STATUS_DISCHARGING;
		publishBattery(level, status, charging ? BatteryManager.BATTERY_PLUGGED_AC : 0);
	}

	/** Publishes the sticky battery broadcast {@code SystemService} reads. */
	@SuppressWarnings("deprecation")
	private void publishBattery(int level, int status, int plugged) {
		final Intent battery = new Intent(Intent.ACTION_BATTERY_CHANGED);
		battery.putExtra(BatteryManager.EXTRA_LEVEL, level);
		battery.putExtra(BatteryManager.EXTRA_SCALE, 100);
		battery.putExtra(BatteryManager.EXTRA_STATUS, status);
		battery.putExtra(BatteryManager.EXTRA_PLUGGED, plugged);
		battery.putExtra(BatteryManager.EXTRA_TEMPERATURE, 300);
		battery.putExtra(BatteryManager.EXTRA_PRESENT, true);
		context.sendStickyBroadcast(battery);
	}

	/** What TalkBack reads for a value cell with the info icon: its own words, then that more is a tap away. */
	private String infoDescription(String value) {
		return context.getString(R.string.battery_value_with_info_cd, value);
	}

	private void setScreenOn(boolean on) {
		shadowOf((PowerManager) context.getSystemService(Context.POWER_SERVICE)).setIsInteractive(on);
	}

	/** Hosts the fragment the way MainActivity does and returns its table. */
	private TableLayout showDetails() {
		final ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class);
		controller.get().setTheme(R.style.AppTheme_NoActionBar);
		final AppCompatActivity activity = controller.setup().get();
		final BatteryDetailsFragment fragment = new BatteryDetailsFragment();
		activity.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment).commitNow();
		return fragment.requireView().findViewById(R.id.batteryDetailsTable);
	}

	/** The value cell of the row labelled {@code labelRes}, or null when the table has no such row. */
	private TextView valueOf(TableLayout table, int labelRes) {
		final String label = context.getString(labelRes);
		for (int i = 0; i < table.getChildCount(); i++) {
			final TableRow row = (TableRow) table.getChildAt(i);
			if (label.equals(((TextView) row.getChildAt(0)).getText().toString())) {
				return (TextView) row.getChildAt(2);
			}
		}
		return null;
	}
}
