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
import android.view.accessibility.AccessibilityEvent;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The notification-details picker (#357), driven through its rows the way a user taps them: what a tap saves, the three-family cap, the empty choice, the
 * live-value column, the toolbar title on the way back, and the status notification being refreshed with each tap and again on the way out — which no
 * preference change did before.
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
		publishBattery(TEMPERATURE);
		controller = Robolectric.buildActivity(SettingsActivity.class).setup();
	}

	@After
	public void tearDown() {
		controller.close();
	}

	@Test
	public void openingThePicker_checksTheSavedChoice() {
		openPicker();
		for (final DetailFamily family : DetailFamily.values()) {
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
	@Config(qualifiers = "ar-rEG")
	public void eachFamily_goesByTheNameTheDetailsTableUses() {
		// One name per thing across the app: a family ticked here as "الحرارة" has to read "الحرارة" in the pull-down and the details table too.
		openPicker();
		assertEquals(context.getString(R.string.temperature), label(DetailFamily.TEMPERATURE));
		assertEquals(context.getString(R.string.battery_current), label(DetailFamily.CURRENT));
		assertEquals(context.getString(R.string.power_source), label(DetailFamily.POWER_SOURCE));
	}

	@Test
	public void theLiveValues_followTheBattery_whileThePickerIsOpen() {
		openPicker();
		publishBattery(412);
		shadowOf(Looper.getMainLooper()).idle();
		assertEquals(TemperatureUtils.format(context, 412), value(DetailFamily.TEMPERATURE));
	}

	@Test
	public void theLiveValues_comeFromTheBroadcastTheyArriveWith() {
		// Sent without updating the sticky copy, so the row reads 41.2 only if the screen used the reading it was handed instead of asking again (#159).
		openPicker();
		context.sendBroadcast(batteryIntent(412));
		shadowOf(Looper.getMainLooper()).idle();
		assertEquals(TemperatureUtils.format(context, 412), value(DetailFamily.TEMPERATURE));
	}

	@Test
	public void aBroadcast_redrawsOnlyTheRowsWhoseValueMoved() {
		// The battery broadcast arrives every few seconds while the screen is open, and usually moves a value or two of the five.
		openPicker();
		final List<Integer> redrawn = new ArrayList<>();
		list().getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
			@Override
			public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
				for (int position = positionStart; position < positionStart + itemCount; position++) {
					redrawn.add(position);
				}
			}
		});
		publishBattery(412);
		shadowOf(Looper.getMainLooper()).idle();
		assertEquals(List.of(DetailFamily.TEMPERATURE.ordinal()), redrawn);
	}

	@Test
	public void tappingARow_rebindsItInPlace_soTalkBackKeepsItsFocus() {
		// A default change animation swaps a fresh view into the row and fades the tapped one out, and TalkBack's focus goes with the old view.
		openPicker();
		final View tapped = row(DetailFamily.SPEED);
		tap(DetailFamily.SPEED);
		assertSame(tapped, row(DetailFamily.SPEED));
	}

	@Test
	public void aRow_readsToTalkBackAsACheckbox_onItsNodeAndItsClickEvent() {
		// TalkBack reads a focused row from its node and announces a tap from the click event. A row that briefed only the node would go out as a plain
		// layout, unchecked, the moment it was ticked.
		openPicker();
		final View ticked = row(DetailFamily.SPEED);
		final AccessibilityNodeInfoCompat node = node(ticked);
		assertEquals(CheckBox.class.getName(), String.valueOf(node.getClassName()));
		assertTrue(node.isCheckable());
		assertEquals(AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE, node.getChecked());
		assertEquals(AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE, node(row(DetailFamily.TEMPERATURE)).getChecked());

		final AccessibilityEvent click = new AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
		ticked.onInitializeAccessibilityEvent(click);
		assertEquals(CheckBox.class.getName(), String.valueOf(click.getClassName()));
		assertTrue(click.isChecked());
	}

	@Test
	@Config(qualifiers = "ar-rEG")
	public void header_keepsWesternDigits_inArabic() {
		// Premise: a region-bearing Arabic locale really localises digits. Bare "ar" picks Western ones and would pass against the defect (#241).
		assertEquals("ar-rEG should select Eastern Arabic digits", "٣", String.format(Locale.getDefault(), "%d", 3));
		openPicker();
		final String text = header().getText().toString();
		assertTrue(text, text.contains("اختر"));
		assertFalse(text, text.matches(".*[٠-٩].*"));
		assertTrue(text, text.contains("3"));
	}

	@Test
	@Config(qualifiers = "sw600dp-w600dp-h960dp")
	public void header_linesUpWithTheRows_onAWideScreen() {
		// Tablets and unfolded foldables pad list rows wider than phones do, so a header inset by a fixed amount drifts off the rows below it.
		openPicker();
		final View row = row(DetailFamily.SPEED);
		assertTrue("rows are not padded wider on this screen", row.getPaddingStart() > dp(16));
		assertEquals(contentStart(row), contentStart(header()));
	}

	@Test
	public void aTap_redrawsTheStatusNotification_whileThePickerIsStillOpen() {
		// Pulling the shade down over the picker doesn't pause it, so a redraw saved for the way out would show the old line meanwhile.
		postStatusNotification();
		openPicker();
		tap(DetailFamily.SPEED);
		tap(DetailFamily.CURRENT);
		tap(DetailFamily.TIME);
		tap(DetailFamily.POWER_SOURCE);

		// Before: no rate yet, so the default choice fell back to the temperature. Now: the one family chosen.
		assertEquals("On battery", statusText());
	}

	@Test
	public void leaving_redrawsItOnceMore_soAnUpdateTheSystemDroppedIsMadeGood() {
		// The system drops a notification update that follows the last one too closely, so a fast run of taps can leave the shade on a choice from the middle.
		postStatusNotification();
		final Notification stale = posted();
		openPicker();
		tap(DetailFamily.SPEED);
		tap(DetailFamily.CURRENT);
		tap(DetailFamily.TIME);
		tap(DetailFamily.POWER_SOURCE);
		manager.notify(NotificationService.getOngoingNotificationId(), stale);

		controller.pause();

		assertEquals("On battery", statusText());
	}

	@Test
	public void leaving_postsNothing_whenThisVisitTappedNothing() {
		postStatusNotification();
		openPicker();
		tap(DetailFamily.SPEED);
		controller.pause();
		// Back on the picker, from the launcher say. The earlier visit's taps were made good when it ended.
		controller.resume();
		shadowOf(Looper.getMainLooper()).idle();
		final Notification before = posted();

		controller.pause();

		assertSame(before, posted());
	}

	@Test
	public void nothingIsPosted_whenNoStatusNotificationIsUp() {
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
	public void goingBack_fromThePicker_restoresTheTitleOfTheScreenBelow() {
		// The picker is the first settings screen two levels deep, so Back lands on another sub-screen rather than the root — the one case the old
		// "reset the title at the root" rule never reached.
		openGeneralFromTheRoot();
		openThePickerFromGeneral();
		assertEquals(context.getString(R.string.notification_details_title), String.valueOf(controller.get().getTitle()));

		controller.get().getOnBackPressedDispatcher().onBackPressed();
		shadowOf(Looper.getMainLooper()).idle();
		assertEquals(context.getString(R.string.pref_header_general), String.valueOf(controller.get().getTitle()));

		controller.get().getOnBackPressedDispatcher().onBackPressed();
		shadowOf(Looper.getMainLooper()).idle();
		assertEquals(context.getString(R.string.title_activity_settings), String.valueOf(controller.get().getTitle()));
	}

	@Test
	public void afterALanguageSwitch_theTitlesFollowIt_onTheWayBackToo() {
		// Switching the app's language (on General itself) recreates Settings with General on top. A title kept as text from before the switch comes back in
		// the old language: right after the switch, and on every Back to General from the picker.
		final String english = context.getString(R.string.pref_header_general);
		openGeneralFromTheRoot();
		RuntimeEnvironment.setQualifiers("+ar-rEG");
		controller.recreate();
		shadowOf(Looper.getMainLooper()).idle();
		final String general = controller.get().getString(R.string.pref_header_general);
		assertNotEquals("the language did not switch", english, general);
		assertEquals(general, String.valueOf(controller.get().getTitle()));

		openThePickerFromGeneral();
		controller.get().getOnBackPressedDispatcher().onBackPressed();
		shadowOf(Looper.getMainLooper()).idle();
		assertEquals(general, String.valueOf(controller.get().getTitle()));
	}

	@Test
	public void generalScreen_opensThePicker() {
		generalScreenEntry().performClick();
		shadowOf(Looper.getMainLooper()).idle();

		assertTrue(controller.get().getSupportFragmentManager().findFragmentById(R.id.settings_container) instanceof NotificationDetailsFragment);
		assertEquals(context.getString(R.string.notification_details_title), String.valueOf(controller.get().getTitle()));
	}

	// --- Helpers ----------------------------------------------------------------------------------

	/** Publish {@link #batteryIntent} as the sticky broadcast {@code SystemService} reads and the picker listens for. */
	@SuppressWarnings("deprecation")
	private void publishBattery(int temperature) {
		context.sendStickyBroadcast(batteryIntent(temperature));
	}

	/** A plugged-out battery at 85%. */
	private static Intent batteryIntent(int temperature) {
		final Intent battery = new Intent(Intent.ACTION_BATTERY_CHANGED);
		battery.putExtra(BatteryManager.EXTRA_LEVEL, 85);
		battery.putExtra(BatteryManager.EXTRA_SCALE, 100);
		battery.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING);
		battery.putExtra(BatteryManager.EXTRA_PLUGGED, 0);
		battery.putExtra(BatteryManager.EXTRA_TEMPERATURE, temperature);
		battery.putExtra(BatteryManager.EXTRA_PRESENT, true);
		return battery;
	}

	/** Put the status notification up, as the monitoring service keeps it. */
	private void postStatusNotification() {
		final BatteryDO batteryDO = SystemService.getBatteryInfo(context);
		NotificationService.updateOngoingNotification(context, batteryDO, BatteryRateTracker.getRate(context, batteryDO));
		assertEquals(TemperatureUtils.format(context, TEMPERATURE), statusText());
	}

	private Notification posted() {
		final Notification posted = shadowOf(manager).getNotification(NotificationService.getOngoingNotificationId());
		assertNotNull("no status notification is up", posted);
		return posted;
	}

	private String statusText() {
		return String.valueOf(posted().extras.getCharSequence(Notification.EXTRA_TEXT));
	}

	private void openPicker() {
		controller.get().getSupportFragmentManager().beginTransaction().replace(R.id.settings_container, new NotificationDetailsFragment()).commitNow();
		shadowOf(Looper.getMainLooper()).idle();
	}

	/** Open General the way a user does, from the root screen's row, so the back stack is the one production builds. */
	private void openGeneralFromTheRoot() {
		final Fragment root = controller.get().getSupportFragmentManager().findFragmentById(R.id.settings_container);
		assertTrue("the root settings screen is not showing", root instanceof SettingsActivity.HeaderFragment);
		((PreferenceFragmentCompat) root).findPreference("general").performClick();
		shadowOf(Looper.getMainLooper()).idle();
	}

	private void openThePickerFromGeneral() {
		SettingsScreen.current(controller).findPreference(context.getString(R.string._pref_key_notification_details)).performClick();
		shadowOf(Looper.getMainLooper()).idle();
	}

	private Preference generalScreenEntry() {
		SettingsScreen.show(controller, R.string.pref_category_general);
		final Preference entry = SettingsScreen.current(controller).findPreference(context.getString(R.string._pref_key_notification_details));
		assertNotNull("the General screen has no Notification details entry", entry);
		return entry;
	}

	private RecyclerView list() {
		return controller.get().findViewById(R.id.notification_details_list);
	}

	private View row(DetailFamily family) {
		final RecyclerView.ViewHolder holder = list().findViewHolderForAdapterPosition(family.ordinal());
		assertNotNull("no row is laid out for " + family, holder);
		return holder.itemView;
	}

	private static AccessibilityNodeInfoCompat node(View row) {
		return AccessibilityNodeInfoCompat.wrap(row.createAccessibilityNodeInfo());
	}

	/** Where a view's content begins across the window: its left edge plus its start padding, on a left-to-right screen. */
	private static int contentStart(View view) {
		final int[] location = new int[2];
		view.getLocationInWindow(location);
		return location[0] + view.getPaddingStart();
	}

	private int dp(int dp) {
		return Math.round(dp * context.getResources().getDisplayMetrics().density);
	}

	private void tap(DetailFamily family) {
		row(family).performClick();
		shadowOf(Looper.getMainLooper()).idle();
	}

	private CheckBox checkBox(DetailFamily family) {
		return row(family).findViewById(R.id.detail_checkbox);
	}

	private String label(DetailFamily family) {
		return ((TextView) row(family).findViewById(R.id.detail_label)).getText().toString();
	}

	private String value(DetailFamily family) {
		return ((TextView) row(family).findViewById(R.id.detail_value)).getText().toString();
	}

	private TextView header() {
		return controller.get().findViewById(R.id.notification_details_header);
	}
}
