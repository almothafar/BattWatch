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
 * The details table's Screen on / Screen off rows (#193). Like the other live rows (#188), they are always there while discharging: a state still gathering
 * time shows the small "calculating" hint with an info icon instead of the row appearing later and pushing the table down. While charging there is no
 * split to show, so neither row is there.
 * <p>
 * The fragment feeds the tracker itself, through {@code BatteryRateTracker.record} with the screen state Robolectric's PowerManager reports. Robolectric
 * leaves the wall clock real for app code, so a past stretch of screen-off is recorded through {@link ScreenDrainTestSupport} and the fragment's own
 * observation closes it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BatteryDetailsScreenDrainRowsTest {

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
			assertEquals("a calculating row carries the info icon", context.getString(R.string.battery_value_info_cd), value.getContentDescription());
		}
	}

	@Test
	public void aStateThatHasRunLongEnough_showsItsDrain_whileTheOtherStillCalculates() {
		// The screen went off two hours ago at 80%; it is on again now, at 78%, as the details table opens.
		publishBattery(80, false);
		ScreenDrainTestSupport.recordAt(context, System.currentTimeMillis() - 2 * 3_600_000L, SystemService.getBatteryInfo(context), false);
		publishBattery(78, false);
		setScreenOn(true);
		final TableLayout table = showDetails();

		assertEquals("2% in two hours off", "1.0%/h", valueOf(table, R.string.screen_off_drain).getText().toString());
		assertNull("a ready row has no info icon", valueOf(table, R.string.screen_off_drain).getContentDescription());
		assertEquals(context.getString(R.string.battery_value_calculating), valueOf(table, R.string.screen_on_drain).getText().toString());
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
