package com.almothafar.simplebatterynotifier.receiver;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.LocaleList;
import android.os.Looper;

import androidx.core.app.AppLocalesStorageHelper;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.BidiVisualOrder;
import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.service.PowerConnectionService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The notifications speak the in-app language on Android 8–12 (#381), where the system applies the choice only to the app's own screens. Each test stores the
 * choice where AppCompat keeps it and opens no screen, as when the monitoring service starts on boot, then reads what the shade would show. One test per path
 * that posts: the monitoring service, {@link BatteryLevelReceiver} and {@link PowerConnectionReceiver}.
 */
@RunWith(RobolectricTestRunner.class)
// Android 10, what the Mate 10 Pro runs. From Android 13 the system applies the choice itself, so the defect cannot show on the sdk 34 the other tests use.
@Config(sdk = 29)
public class NotificationLanguageTest {

	private Context context;
	private LocaleList phoneLocales;
	private ServiceController<PowerConnectionService> service;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		phoneLocales = LocaleList.getDefault();
		PowerConnectionReceiver.setCurrentState(-1);
		PowerConnectionReceiver.cancelPendingSample();
		// Premise: the phone speaks English, so Arabic in the shade can only have come from the app's own choice.
		assertEquals("Discharging", context.getString(R.string.discharging));
	}

	@After
	public void tearDown() {
		if (nonNull(service)) {
			service.destroy();
		}
		PowerConnectionReceiver.cancelPendingSample();
		LocaleList.setDefault(phoneLocales);
	}

	@Test
	public void theMonitoringService_postsTheStatusNotification_inTheAppLanguage() {
		chooseAppLanguage("ar");
		publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 85, 0);

		service = Robolectric.buildService(PowerConnectionService.class).create();

		assertEquals("85% · تستهلك", titleOf(shadowOf(service.get()).getLastForegroundNotification()));
	}

	@Test
	public void theBatteryReceiver_postsTheAlertAndTheStatusNotification_inTheAppLanguage() {
		chooseAppLanguage("ar");

		new BatteryLevelReceiver().onReceive(context, publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 5, 0));

		assertEquals(List.of("5% · تستهلك", "البطارية منخفضة جداً"), postedTitles());
	}

	@Test
	public void thePowerReceiver_announcesCharging_inTheAppLanguage() {
		chooseAppLanguage("ar");

		new PowerConnectionReceiver().onReceive(context, publishBattery(BatteryManager.BATTERY_STATUS_CHARGING, 50, BatteryManager.BATTERY_PLUGGED_AC));
		// Past the whole sampling window. Robolectric reports no charging current, so the speed stays unknown and the plain message is the one shown.
		shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(
				PowerConnectionReceiver.CHARGE_SAMPLE_DELAY_MS * PowerConnectionReceiver.MAX_CHARGE_SAMPLE_ATTEMPTS));

		assertEquals("شحن سلكي", ShadowToast.getTextOfLatestToast());
	}

	/** The detectors' alerts: the receiver's broadcast reaches them through {@code FastDrainDetector} and {@code SlowChargeDetector}. */
	@Test
	public void theFastDrainAlert_isInTheAppLanguage() {
		chooseAppLanguage("ar");

		NotificationService.sendFastDrainNotification(context, 27, 20, 45);

		assertEquals(List.of("البطارية تُستهلك بسرعة"), postedTitles());
	}

	@Test
	public void theSlowChargeWarning_isInTheAppLanguage() {
		chooseAppLanguage("ar");

		NotificationService.sendSlowChargeWarning(context, 3);

		assertEquals("الشحن بطيء (~3 واط) — جرّب كابلاً آخر، أو نظّف المنفذ، أو استخدم شاحناً مختلفاً.",
		             BidiVisualOrder.stripControls(String.valueOf(ShadowToast.getTextOfLatestToast())));
	}

	/**
	 * Arabic words are not enough: a number in Arabic copy needs isolating, or its unit drifts to the far side of it (#275). Whether to isolate follows the
	 * process's default locale, which AppCompat sets to the app's language only while a screen is being opened.
	 */
	@Test
	public void anArabicAlert_keepsTheUnitOnItsReading_withNoScreenOpen() {
		chooseAppLanguage("ar");

		NotificationService.sendTemperatureNotification(context, 460);

		final String text = textOf(shadowOf(notificationManager()).getAllNotifications().get(0));
		assertTrue(text, text.contains("حرارة"));
		BidiVisualOrder.assertRendersAsWritten(text, "46.0 °C");
	}

	@Test
	public void withTheLanguageLeftOnSystem_theNotificationsKeepThePhonesLanguage() {
		new BatteryLevelReceiver().onReceive(context, publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 5, 0));

		assertEquals(List.of("5% · Discharging", "Battery critically low"), postedTitles());
	}

	// --- Helpers ----------------------------------------------------------------------------------

	/**
	 * Store the choice where AppCompat keeps it below Android 13 ({@code autoStoreLocales} in the manifest), as Settings → App language leaves it.
	 *
	 * @param tag the language tag the picker stores, e.g. "ar"
	 */
	// AppCompat's own writer, so the choice is stored in exactly the form the app reads back.
	@SuppressLint("RestrictedApi")
	private void chooseAppLanguage(String tag) {
		AppLocalesStorageHelper.persistLocales(context, tag);
	}

	/** A battery-changed broadcast, published sticky too, since the service and the power receiver read the battery from the sticky copy. */
	@SuppressWarnings("deprecation")
	private Intent publishBattery(int status, int level, int plugged) {
		final Intent battery = new Intent(Intent.ACTION_BATTERY_CHANGED);
		battery.putExtra(BatteryManager.EXTRA_STATUS, status);
		battery.putExtra(BatteryManager.EXTRA_LEVEL, level);
		battery.putExtra(BatteryManager.EXTRA_SCALE, 100);
		battery.putExtra(BatteryManager.EXTRA_PLUGGED, plugged);
		battery.putExtra(BatteryManager.EXTRA_PRESENT, true);
		battery.putExtra(BatteryManager.EXTRA_TEMPERATURE, 250);
		context.sendStickyBroadcast(battery);
		return battery;
	}

	private NotificationManager notificationManager() {
		return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/** The title of every notification up, sorted so the order they were posted in does not matter, as the user reads them: without the bidi controls. */
	private List<String> postedTitles() {
		return shadowOf(notificationManager()).getAllNotifications().stream().map(NotificationLanguageTest::titleOf).sorted().collect(Collectors.toList());
	}

	private static String titleOf(Notification notification) {
		return BidiVisualOrder.stripControls(String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TITLE)));
	}

	private static String textOf(Notification notification) {
		return String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TEXT));
	}
}
