package com.almothafar.simplebatterynotifier.ui.fragment;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.model.DetailFamily;
import com.almothafar.simplebatterynotifier.service.BatteryRateTracker;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.service.SystemService;
import com.almothafar.simplebatterynotifier.ui.SettingsActivity;
import com.almothafar.simplebatterynotifier.util.AppPrefs;
import com.almothafar.simplebatterynotifier.util.TemperatureUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The notification-details picker (#357), driven through its rows the way a user taps them: what a tap saves, the three-family cap, the empty choice, the
 * live-value column, and the status notification being refreshed on the way out — which no preference change did before.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NotificationDetailsPickerTest {

	/** Tenths of a degree, as {@code BatteryManager} reports it. */
	private static final int TEMPERATURE = 346;

	private Context context;
	private NotificationManager manager;
	private ActivityController<SettingsActivity> controller;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
		manager = context.getSystemService(NotificationManager.class);
		publishBattery();
		controller = Robolectric.buildActivity(SettingsActivity.class).setup();
	}

	@After
	public void tearDown() {
		controller.close();
	}

	@Test
	public void openingThePicker_checksTheSavedChoice() {
		openPicker();
		for (DetailFamily family : DetailFamily.values()) {
			assertEquals(family.name(), AppPrefs.DEFAULT_NOTIFICATION_DETAILS.contains(family), checkBox(family).isChecked());
		}
	}

	@Test
	public void tappingARow_savesTheChoiceAtOnce_inTheListOrder() {
		openPicker();
		tap(DetailFamily.SPEED);
		assertEquals(List.of(DetailFamily.CURRENT, DetailFamily.TIME), AppPrefs.notificationDetails(context));
		// Re-adding lands it back in the picker's order, not at the end — reordering is a later slice (#358).
		tap(DetailFamily.SPEED);
		assertEquals(List.of(DetailFamily.SPEED, DetailFamily.CURRENT, DetailFamily.TIME), AppPrefs.notificationDetails(context));
		assertTrue(checkBox(DetailFamily.SPEED).isChecked());
	}

	@Test
	public void atTheCap_theUncheckedRowsAreDisabled_untilOneIsFreed() {
		openPicker();
		assertFalse(row(DetailFamily.TEMPERATURE).isEnabled());
		assertFalse(row(DetailFamily.POWER_SOURCE).isEnabled());
		assertTrue(row(DetailFamily.CURRENT).isEnabled());

		tap(DetailFamily.CURRENT);
		assertTrue(row(DetailFamily.TEMPERATURE).isEnabled());
		assertTrue(row(DetailFamily.POWER_SOURCE).isEnabled());
	}

	@Test
	public void aFourthFamily_isRefused_evenIfTheRowIsReached() {
		// A disabled row can still be clicked programmatically (performClick ignores isEnabled), so the cap has to hold below the view too. Asserted on the row
		// rather than read back through AppPrefs: that read caps at three as well, so it would hide a fourth the picker let through.
		openPicker();
		tap(DetailFamily.TEMPERATURE);
		assertFalse(checkBox(DetailFamily.TEMPERATURE).isChecked());
		assertEquals(context.getString(R.string.notification_details_header, "3", "3"), header().getText().toString());
	}

	@Test
	public void anEmptyChoice_isAllowed_andTheHeaderSaysWhatItMeans() {
		openPicker();
		tap(DetailFamily.SPEED);
		tap(DetailFamily.CURRENT);
		tap(DetailFamily.TIME);
		assertEquals(List.of(), AppPrefs.notificationDetails(context));
		assertEquals(context.getString(R.string.notification_details_header_empty), header().getText().toString());
	}

	@Test
	public void eachRow_carriesItsFamilysLiveValue() {
		openPicker();
		assertEquals(TemperatureUtils.format(context, TEMPERATURE), value(DetailFamily.TEMPERATURE));
		assertEquals("On battery", value(DetailFamily.POWER_SOURCE));
	}

	@Test
	public void leaving_refreshesTheStatusNotification() {
		postStatusNotification();
		openPicker();
		tap(DetailFamily.SPEED);
		tap(DetailFamily.CURRENT);
		tap(DetailFamily.TIME);
		tap(DetailFamily.POWER_SOURCE);

		controller.pause();

		// Before: no rate yet, so the default choice fell back to the temperature. After: the one family now chosen.
		assertEquals("On battery", statusText());
	}

	@Test
	public void leaving_postsNothing_whenNoStatusNotificationIsUp() {
		openPicker();
		tap(DetailFamily.SPEED);

		controller.pause();

		assertEquals(0, manager.getActiveNotifications().length);
	}

	@Test
	public void generalScreen_summarisesTheChoice() {
		final Preference entry = generalScreenEntry();
		assertEquals("Speed · Current · Time", String.valueOf(entry.getSummary()));
		AppPrefs.setNotificationDetails(context, List.of());
		assertEquals(context.getString(R.string.notification_details_summary_none), String.valueOf(entry.getSummary()));
	}

	@Test
	public void generalScreen_opensThePicker() {
		generalScreenEntry().performClick();
		shadowOf(Looper.getMainLooper()).idle();

		assertTrue(controller.get().getSupportFragmentManager().findFragmentById(R.id.settings_container) instanceof NotificationDetailsFragment);
		assertEquals(context.getString(R.string.notification_details_title), String.valueOf(controller.get().getTitle()));
	}

	// --- Helpers ----------------------------------------------------------------------------------

	/** A plugged-out battery at 85%, published as the sticky broadcast {@code SystemService} reads. */
	@SuppressWarnings("deprecation")
	private void publishBattery() {
		final Intent battery = new Intent(Intent.ACTION_BATTERY_CHANGED);
		battery.putExtra(BatteryManager.EXTRA_LEVEL, 85);
		battery.putExtra(BatteryManager.EXTRA_SCALE, 100);
		battery.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING);
		battery.putExtra(BatteryManager.EXTRA_PLUGGED, 0);
		battery.putExtra(BatteryManager.EXTRA_TEMPERATURE, TEMPERATURE);
		battery.putExtra(BatteryManager.EXTRA_PRESENT, true);
		context.sendStickyBroadcast(battery);
	}

	/** Put the status notification up, as the monitoring service keeps it. */
	private void postStatusNotification() {
		final BatteryDO batteryDO = SystemService.getBatteryInfo(context);
		NotificationService.updateOngoingNotification(context, batteryDO, BatteryRateTracker.getRate(context, batteryDO));
		assertEquals(TemperatureUtils.format(context, TEMPERATURE), statusText());
	}

	private String statusText() {
		final Notification posted = shadowOf(manager).getNotification(NotificationService.getOngoingNotificationId());
		assertNotNull("no status notification is up", posted);
		return String.valueOf(posted.extras.getCharSequence(Notification.EXTRA_TEXT));
	}

	private void openPicker() {
		controller.get().getSupportFragmentManager().beginTransaction().replace(R.id.settings_container, new NotificationDetailsFragment()).commitNow();
		shadowOf(Looper.getMainLooper()).idle();
	}

	private Preference generalScreenEntry() {
		SettingsScreen.show(controller, R.string.pref_category_general);
		final Preference entry = SettingsScreen.current(controller).findPreference(context.getString(R.string._pref_key_notification_details));
		assertNotNull("the General screen has no Notification details entry", entry);
		return entry;
	}

	private View row(DetailFamily family) {
		final RecyclerView list = controller.get().findViewById(R.id.notification_details_list);
		final RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(family.ordinal());
		assertNotNull("no row is laid out for " + family, holder);
		return holder.itemView;
	}

	private void tap(DetailFamily family) {
		row(family).performClick();
		shadowOf(Looper.getMainLooper()).idle();
	}

	private CheckBox checkBox(DetailFamily family) {
		return row(family).findViewById(R.id.detail_checkbox);
	}

	private String value(DetailFamily family) {
		return ((TextView) row(family).findViewById(R.id.detail_value)).getText().toString();
	}

	private TextView header() {
		return controller.get().findViewById(R.id.notification_details_header);
	}
}
