package com.almothafar.simplebatterynotifier.ui.fragment;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.BatteryManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.almothafar.simplebatterynotifier.service.OngoingStatusContent.DETAIL_SEPARATOR;
import static java.util.Objects.isNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The notification-details picker (#357), driven through its rows the way a user taps them: what a tap saves, the three-family cap, the empty choice, the
 * live-value column, the toolbar title on the way back, and the status notification being refreshed with each tap and again on the way out — which no
 * preference change did before.
 */
@RunWith(RobolectricTestRunner.class)
// Tall enough for the whole page, so a drag never strays into the screen's edge zone, where the page scrolls under it. The one test about that scroll, and
// those that need a locale or a wide screen, name their own qualifiers, which replace these.
@Config(sdk = 34, qualifiers = "h800dp")
public class NotificationDetailsPickerTest {

	/** Tenths of a degree, as {@code BatteryManager} reports it. */
	private static final int TEMPERATURE = 346;
	private static final String MOVE_UP = "Move up";
	private static final String MOVE_DOWN = "Move down";

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
	public void theChosenRows_floatToTheTop_inTheSavedOrder_aboveTheDivider() {
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.TEMPERATURE, DetailFamily.SPEED));
		openPicker();
		assertEquals("TEMPERATURE SPEED | CURRENT TIME POWER_SOURCE", screen());
	}

	@Test
	public void tappingARow_savesTheChoiceAtOnce_andATickedFamilyJoinsTheEnd() {
		openPicker();
		tap(DetailFamily.SPEED);
		assertEquals(List.of(DetailFamily.CURRENT, DetailFamily.TIME), AppPrefs.notificationDetails(context));
		assertEquals("CURRENT TIME | SPEED TEMPERATURE POWER_SOURCE", screen());

		// At the end rather than back in registry order, which would undo whatever order the user had set (#358).
		tap(DetailFamily.SPEED);
		assertEquals(List.of(DetailFamily.CURRENT, DetailFamily.TIME, DetailFamily.SPEED), AppPrefs.notificationDetails(context));
		assertEquals("CURRENT TIME SPEED | TEMPERATURE POWER_SOURCE", screen());
		assertTrue(checkBox(DetailFamily.SPEED).isChecked());
	}

	@Test
	public void draggingAChosenRowByItsHandle_reordersTheChoice_andRedrawsTheNotification() {
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE));
		postStatusNotification();
		openPicker();

		drag(DetailFamily.TEMPERATURE, row(DetailFamily.POWER_SOURCE));

		assertEquals(List.of(DetailFamily.POWER_SOURCE, DetailFamily.TEMPERATURE), AppPrefs.notificationDetails(context));
		assertEquals("POWER_SOURCE TEMPERATURE | SPEED CURRENT TIME", screen());
		assertEquals("On battery" + DETAIL_SEPARATOR + TemperatureUtils.format(context, TEMPERATURE), statusText());
	}

	@Test
	public void draggingAnUnchosenRowAboveTheDivider_ticksItWhereItIsDropped() {
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE));
		openPicker();

		drag(DetailFamily.SPEED, row(DetailFamily.POWER_SOURCE));

		assertEquals(List.of(DetailFamily.TEMPERATURE, DetailFamily.SPEED, DetailFamily.POWER_SOURCE), AppPrefs.notificationDetails(context));
		assertEquals("TEMPERATURE SPEED POWER_SOURCE | CURRENT TIME", screen());
		assertTrue(checkBox(DetailFamily.SPEED).isChecked());
		assertEquals(context.getString(R.string.notification_details_header, "3", "3"), header().getText().toString());
	}

	@Test
	public void draggingAChosenRowBelowTheDivider_unticksIt_andItFallsIntoItsPlace() {
		openPicker();

		drag(DetailFamily.SPEED, row(DetailFamily.TEMPERATURE));

		assertEquals(List.of(DetailFamily.CURRENT, DetailFamily.TIME), AppPrefs.notificationDetails(context));
		// Dropped after Temperature, but only the chosen rows keep an order: the rest stay in the order they are listed in.
		assertEquals("CURRENT TIME | SPEED TEMPERATURE POWER_SOURCE", screen());
		assertFalse(checkBox(DetailFamily.SPEED).isChecked());
	}

	@Test
	public void aRowDraggedAcrossTheDivider_showsItsNewStateBeforeTheDrop() {
		// The row is ticked the moment it crosses the line, as a Quick Settings tile changes as it crosses into the panel, not when the finger lifts.
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE));
		openPicker();
		final Finger finger = new Finger(DetailFamily.SPEED);

		finger.moveOver(row(DetailFamily.POWER_SOURCE));

		assertTrue(checkBox(DetailFamily.SPEED).isChecked());
		assertEquals(context.getString(R.string.notification_details_header, "3", "3"), header().getText().toString());
		assertFalse("the cap is reached, so the rows still below go grey", row(DetailFamily.CURRENT).isEnabled());
		assertEquals("saved before the drop", List.of(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE), AppPrefs.notificationDetails(context));
		finger.letGo();
	}

	@Test
	public void atTheCap_anUnchosenRow_doesNotDrag() {
		openPicker();

		drag(DetailFamily.TEMPERATURE, row(DetailFamily.TIME));

		assertEquals(AppPrefs.DEFAULT_NOTIFICATION_DETAILS, AppPrefs.notificationDetails(context));
		assertEquals("SPEED CURRENT TIME | TEMPERATURE POWER_SOURCE", screen());
	}

	@Test
	@Config(qualifiers = "h400dp")
	public void aRowHeldAtTheScreensBottom_scrollsThePage_andStaysUnderTheFinger() {
		// A short screen, or a large font: the page is taller than the window, so a row's new place can be out of sight below. The list lays out every row,
		// so ItemTouchHelper's own edge scrolling has nothing to scroll; the page has to.
		openPicker();
		final NestedScrollView page = controller.get().findViewById(R.id.notification_details_page);
		assertTrue("the page fits the screen, so there is nothing to scroll", page.canScrollVertically(1));
		final View dragged = row(DetailFamily.SPEED);
		final Finger finger = new Finger(DetailFamily.SPEED);
		// Just inside the bottom edge zone. Any deeper and, at the page's end, the row is carried past the list's last edge, where ItemTouchHelper's own
		// scrolling never ends under Robolectric (see Finger).
		final float fingerAt = windowY(page) + page.getHeight() - dp(44);

		finger.moveTo(fingerAt);

		// Robolectric runs these frames without its clock moving, so a finger held in the edge zone takes the page to its end at once, where a screen takes a
		// pixel a frame. Well past one step, then: each step's move event is what books the next.
		assertTrue("the page scrolled " + page.getScrollY() + "px", page.getScrollY() > dp(10));
		// The list moved up under a resting finger; the row has to have moved down the list to match, or it rides off with the page.
		drawFrame();
		assertEquals("the row slid away from the finger", fingerAt, windowY(dragged) + finger.grip(), 1.5f);
		// Not checked here: that no step is taken once the row is dropped. The page is already at its end by then, for the reason above, so one step too many
		// would have nowhere to go.
		finger.letGo();
	}

	@Test
	public void aTouchOnTheHandle_thatNeverMoves_leavesTheRowAsItWas() {
		// The handle sits inside the row, and the row ticks on a click: a touch the handle let through would tick or untick the row it was meant to move.
		openPicker();
		new Finger(DetailFamily.SPEED).letGo();
		assertEquals(AppPrefs.DEFAULT_NOTIFICATION_DETAILS, AppPrefs.notificationDetails(context));
		assertTrue(checkBox(DetailFamily.SPEED).isChecked());
	}

	@Test
	public void aRowDroppedAmongTheUnchosen_fallsBackIntoItsPlace_andNothingIsSaved() {
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.SPEED));
		postStatusNotification();
		openPicker();
		final Notification before = posted();

		drag(DetailFamily.CURRENT, row(DetailFamily.TEMPERATURE));

		assertEquals("SPEED | CURRENT TIME TEMPERATURE POWER_SOURCE", screen());
		assertSame(before, posted());
	}

	@Test
	public void aChosenRow_offersTalkBackMoveUpAndMoveDown_onlyWhereItCanGo() {
		// Drag is out of reach with TalkBack, so these actions are the only way a screen-reader user can reorder. The rows below the divider have no order to
		// change; a double-tap ticks them onto the end.
		openPicker();
		assertEquals(List.of(MOVE_DOWN), moveActions(DetailFamily.SPEED));
		assertEquals(List.of(MOVE_UP, MOVE_DOWN), moveActions(DetailFamily.CURRENT));
		assertEquals(List.of(MOVE_UP), moveActions(DetailFamily.TIME));
		assertEquals(List.of(), moveActions(DetailFamily.TEMPERATURE));
	}

	@Test
	public void aChosenRow_tellsTalkBackItsPlace_andAMoveUpdatesIt() {
		// After Move up the focus stays on the row; its new place, spoken as its state, is what tells a screen-reader user the move happened.
		openPicker();
		assertEquals(context.getString(R.string.notification_details_place, "2", "3"), stateOf(DetailFamily.CURRENT));
		assertNull("an unchosen row keeps TalkBack's own \"not checked\"", stateOf(DetailFamily.TEMPERATURE));

		talkBack(DetailFamily.TIME, R.id.notification_details_action_move_up);

		assertEquals(context.getString(R.string.notification_details_place, "2", "3"), stateOf(DetailFamily.TIME));
		assertEquals(context.getString(R.string.notification_details_place, "3", "3"), stateOf(DetailFamily.CURRENT));
	}

	@Test
	public void aTap_givesTheRowItsPlace_beforeTheListRedraws() {
		// TalkBack reads a tap from the click, which goes out before the list's next layout rebinds the row. A place that only arrived with the rebind would be
		// announced a second time, as a change.
		openPicker();
		tap(DetailFamily.SPEED);
		final View ticked = row(DetailFamily.TEMPERATURE);

		ticked.performClick();

		assertEquals(context.getString(R.string.notification_details_place, "3", "3"), String.valueOf(node(ticked).getStateDescription()));
		shadowOf(Looper.getMainLooper()).idle();
	}

	@Test
	@Config(qualifiers = "ar-rEG")
	public void aChosenRowsPlace_keepsWesternDigits_inArabic() {
		// Premise: a region-bearing Arabic locale really localises digits. Bare "ar" picks Western ones and would pass against the defect (#241).
		assertEquals("ar-rEG should select Eastern Arabic digits", "٢", String.format(Locale.getDefault(), "%d", 2));
		openPicker();
		final String place = stateOf(DetailFamily.CURRENT);
		assertFalse(place, place.matches(".*[٠-٩].*"));
		assertTrue(place, place.contains("2") && place.contains("3"));
	}

	@Test
	public void moveUp_reordersTheChoice_savesIt_andRedrawsTheNotification() {
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE));
		postStatusNotification();
		openPicker();
		final View moved = row(DetailFamily.POWER_SOURCE);

		assertTrue(talkBack(DetailFamily.POWER_SOURCE, R.id.notification_details_action_move_up));

		assertEquals(List.of(DetailFamily.POWER_SOURCE, DetailFamily.TEMPERATURE), AppPrefs.notificationDetails(context));
		assertEquals("POWER_SOURCE TEMPERATURE | SPEED CURRENT TIME", screen());
		assertEquals("On battery" + DETAIL_SEPARATOR + TemperatureUtils.format(context, TEMPERATURE), statusText());
		// The same view travels up, so TalkBack's focus goes with the row it just moved.
		assertSame(moved, row(DetailFamily.POWER_SOURCE));
	}

	@Test
	public void moveUp_arrivesAtTheOrderADragDoes() {
		openPicker();
		drag(DetailFamily.TIME, row(DetailFamily.CURRENT));
		final List<DetailFamily> dragged = AppPrefs.notificationDetails(context);
		assertEquals(List.of(DetailFamily.SPEED, DetailFamily.TIME, DetailFamily.CURRENT), dragged);

		AppPrefs.setNotificationDetails(context, AppPrefs.DEFAULT_NOTIFICATION_DETAILS);
		openPicker();
		talkBack(DetailFamily.TIME, R.id.notification_details_action_move_up);

		assertEquals(dragged, AppPrefs.notificationDetails(context));
	}

	@Test
	public void aReorder_survivesTheScreenBeingRecreated() {
		openPicker();
		talkBack(DetailFamily.TIME, R.id.notification_details_action_move_up);

		controller.recreate();
		shadowOf(Looper.getMainLooper()).idle();

		assertEquals("SPEED TIME CURRENT | TEMPERATURE POWER_SOURCE", screen());
	}

	@Test
	public void moveDown_arrivesAtTheOrderADragDoes() {
		openPicker();
		drag(DetailFamily.SPEED, row(DetailFamily.CURRENT));
		final List<DetailFamily> dragged = AppPrefs.notificationDetails(context);
		assertEquals(List.of(DetailFamily.CURRENT, DetailFamily.SPEED, DetailFamily.TIME), dragged);

		AppPrefs.setNotificationDetails(context, AppPrefs.DEFAULT_NOTIFICATION_DETAILS);
		openPicker();
		assertTrue(talkBack(DetailFamily.SPEED, R.id.notification_details_action_move_down));

		assertEquals(dragged, AppPrefs.notificationDetails(context));
	}

	@Test
	public void thePreview_showsTheNotificationTheChoiceMakes_andFollowsAMove() {
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE));
		postStatusNotification();
		openPicker();
		assertEquals(String.valueOf(posted().extras.getCharSequence(Notification.EXTRA_TITLE)), previewTitle().getText().toString());
		assertEquals(TemperatureUtils.format(context, TEMPERATURE) + DETAIL_SEPARATOR + "On battery", previewLine().getText().toString());

		talkBack(DetailFamily.POWER_SOURCE, R.id.notification_details_action_move_up);

		assertEquals("On battery" + DETAIL_SEPARATOR + TemperatureUtils.format(context, TEMPERATURE), previewLine().getText().toString());
		assertEquals(statusText(), previewLine().getText().toString());
	}

	@Test
	public void thePreview_followsADrag_beforeTheDropSavesIt() {
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE));
		openPicker();
		final Finger finger = new Finger(DetailFamily.TEMPERATURE);

		finger.moveOver(row(DetailFamily.POWER_SOURCE));

		assertEquals("On battery" + DETAIL_SEPARATOR + TemperatureUtils.format(context, TEMPERATURE), previewLine().getText().toString());
		assertEquals("saved before the drop", List.of(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE), AppPrefs.notificationDetails(context));

		finger.letGo();

		assertEquals(List.of(DetailFamily.POWER_SOURCE, DetailFamily.TEMPERATURE), AppPrefs.notificationDetails(context));
	}

	@Test
	public void thePreview_followsTheBattery_whileThePickerIsOpen() {
		openPicker();
		publishBattery(412);
		shadowOf(Looper.getMainLooper()).idle();
		// No rate yet, so the default choice falls back to the temperature.
		assertEquals(TemperatureUtils.format(context, 412), previewLine().getText().toString());
	}

	@Test
	public void thePreview_readsToTalkBackAsOneItem_namedForWhatItIs() {
		// Read line by line and unnamed, it would sound like a notification had just arrived.
		openPicker();
		final AccessibilityNodeInfoCompat node = node(controller.get().findViewById(R.id.notification_details_preview));
		assertTrue(node.isScreenReaderFocusable());
		assertEquals(context.getString(R.string.notification_details_preview), String.valueOf(node.getRoleDescription()));
	}

	@Test
	public void anEmptyChoice_previewsTheTitleAlone() {
		openPicker();
		tap(DetailFamily.SPEED);
		tap(DetailFamily.CURRENT);
		tap(DetailFamily.TIME);
		assertEquals(View.VISIBLE, previewTitle().getVisibility());
		assertEquals(View.GONE, previewLine().getVisibility());
	}

	@Test
	public void reset_restoresTheDefault_andRedrawsTheNotification() {
		AppPrefs.setNotificationDetails(context, List.of(DetailFamily.POWER_SOURCE, DetailFamily.TEMPERATURE));
		postStatusNotification();
		openPicker();

		assertTrue(toolbarMenu().performIdentifierAction(R.id.action_reset_notification_details, 0));
		shadowOf(Looper.getMainLooper()).idle();

		assertEquals(List.of(DetailFamily.SPEED, DetailFamily.CURRENT, DetailFamily.TIME), AppPrefs.notificationDetails(context));
		assertEquals("SPEED CURRENT TIME | TEMPERATURE POWER_SOURCE", screen());
		// No rate yet, so the default choice falls back to the temperature.
		assertEquals(TemperatureUtils.format(context, TEMPERATURE), statusText());
	}

	@Test
	public void reset_isGreyedOut_whileTheChoiceIsTheDefault() {
		openPicker();
		assertFalse(resetItem().isEnabled());
		assertFalse("a greyed-out Reset still acted", toolbarMenu().performIdentifierAction(R.id.action_reset_notification_details, 0));

		// The menu is rebuilt when it changes, so each look finds the item afresh.
		tap(DetailFamily.SPEED);
		assertTrue(resetItem().isEnabled());
	}

	@Test
	public void reset_leavesTheToolbar_withThePicker() {
		openGeneralFromTheRoot();
		openThePickerFromGeneral();
		assertNotNull(toolbarMenu().findItem(R.id.action_reset_notification_details));

		controller.get().getOnBackPressedDispatcher().onBackPressed();
		shadowOf(Looper.getMainLooper()).idle();

		assertNull(toolbarMenu().findItem(R.id.action_reset_notification_details));
	}

	@Test
	public void atTheCap_theUncheckedRowsAreDisabled_untilOneIsFreed() {
		openPicker();
		assertFalse(row(DetailFamily.TEMPERATURE).isEnabled());
		assertFalse(row(DetailFamily.POWER_SOURCE).isEnabled());
		assertFalse("the handle stays lit on a row that cannot move", handle(DetailFamily.TEMPERATURE).isEnabled());
		assertTrue(row(DetailFamily.CURRENT).isEnabled());

		tap(DetailFamily.CURRENT);
		assertTrue(row(DetailFamily.TEMPERATURE).isEnabled());
		assertTrue(row(DetailFamily.POWER_SOURCE).isEnabled());
		assertTrue(handle(DetailFamily.TEMPERATURE).isEnabled());
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
		assertEquals(List.of(list().getChildAdapterPosition(row(DetailFamily.TEMPERATURE))), redrawn);
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
		assertEquals(TemperatureUtils.format(context, TEMPERATURE), statusText());
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
		posted();
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

	/** The toolbar's menu, as the options menu last laid it out. */
	private Menu toolbarMenu() {
		shadowOf(Looper.getMainLooper()).idle();
		return ((Toolbar) controller.get().findViewById(R.id.toolbar)).getMenu();
	}

	private MenuItem resetItem() {
		final MenuItem reset = toolbarMenu().findItem(R.id.action_reset_notification_details);
		assertNotNull("the toolbar has no Reset", reset);
		return reset;
	}

	private RecyclerView list() {
		return controller.get().findViewById(R.id.notification_details_list);
	}

	/** A family's row, found the way a user finds it: by the name it shows. */
	private View row(DetailFamily family) {
		for (final View row : rowsFromTheTop()) {
			if (!isDivider(row) && familyOf(row) == family) {
				return row;
			}
		}
		throw new AssertionError("no row is laid out for " + family);
	}

	/** The list as it reads from the top: each row's family, and "|" for the divider between the chosen rows and the rest. */
	private String screen() {
		final List<String> shown = new ArrayList<>();
		for (final View row : rowsFromTheTop()) {
			shown.add(isDivider(row) ? "|" : familyOf(row).name());
		}
		return String.join(" ", shown);
	}

	private static boolean isDivider(View row) {
		return row.getId() == R.id.notification_details_divider;
	}

	/** The family a row shows, by its name. */
	private DetailFamily familyOf(View row) {
		final CharSequence name = ((TextView) row.findViewById(R.id.detail_label)).getText();
		for (final DetailFamily family : DetailFamily.values()) {
			if (context.getString(NotificationDetailsFragment.labelRes(family)).contentEquals(name)) {
				return family;
			}
		}
		throw new AssertionError("a row shows an unknown name: " + name);
	}

	/** The list's children in on-screen order, by where layout put them rather than where a move animation has them right now. */
	private List<View> rowsFromTheTop() {
		final RecyclerView list = list();
		final List<View> rows = new ArrayList<>();
		for (int index = 0; index < list.getChildCount(); index++) {
			rows.add(list.getChildAt(index));
		}
		rows.sort(Comparator.comparingInt(View::getTop));
		return rows;
	}

	private static AccessibilityNodeInfoCompat node(View row) {
		return AccessibilityNodeInfoCompat.wrap(row.createAccessibilityNodeInfo());
	}

	/** The state TalkBack reads for a family's row in place of plain "checked"; null when the row leaves it to TalkBack. */
	private String stateOf(DetailFamily family) {
		final CharSequence state = node(row(family)).getStateDescription();
		return isNull(state) ? null : state.toString();
	}

	/** The move actions TalkBack offers on a family's row, each as the label it reads out, in the order the row lists them. */
	private List<String> moveActions(DetailFamily family) {
		final List<String> labels = new ArrayList<>();
		for (final AccessibilityNodeInfoCompat.AccessibilityActionCompat action : node(row(family)).getActionList()) {
			if (action.getId() == R.id.notification_details_action_move_up || action.getId() == R.id.notification_details_action_move_down) {
				labels.add(String.valueOf(action.getLabel()));
			}
		}
		return labels;
	}

	/** Drag a family's row by its handle into another row's place and let go there. */
	private void drag(DetailFamily family, View target) {
		final Finger finger = new Finger(family);
		finger.moveOver(target);
		finger.letGo();
	}

	/**
	 * A finger on a row's handle, driving ItemTouchHelper through the list's real touch handling: it presses on construction, makes one stroke in small steps
	 * with a frame between each so the list lays out as it goes, and lets go where the stroke ended.
	 * <p>
	 * The stroke must not take the row past the first or last row. That means holding it beyond the list's edge, where ItemTouchHelper scrolls the list and
	 * re-posts itself for the next frame, and under Robolectric that loop never ends: the run dies of an OutOfMemoryError.
	 * <p>
	 * It keeps its place on the screen, as a real finger does, and works out where that is on the list for each event: when the page scrolls, the list moves
	 * under a finger that has not moved.
	 */
	private final class Finger {

		private static final int STEPS = 12;

		private final long downTime = SystemClock.uptimeMillis();
		private final View row;
		private final float x;
		/** Where the finger pressed, down the window. */
		private final float pressedAt;
		/** Where on its row the finger holds it, from the row's top. */
		private final float grip;
		/** Where the finger is now, down the window. */
		private float at;

		Finger(DetailFamily family) {
			row = row(family);
			final View handle = handle(family);
			x = row.getLeft() + handle.getLeft() + handle.getWidth() / 2f;
			pressedAt = windowY(handle) + handle.getHeight() / 2f;
			grip = pressedAt - windowY(row);
			at = pressedAt;
			touch(MotionEvent.ACTION_DOWN);
		}

		/** Move until the row is just past the target's far edge. Measured from the layout before the stroke, so it is the finger's one stroke. */
		void moveOver(View target) {
			moveBy(target.getTop() < row.getTop() ? target.getTop() - row.getTop() - 2 : target.getBottom() - row.getBottom() + 2);
		}

		/** Move the finger to a place down the window, in one stroke. */
		void moveTo(float windowY) {
			moveBy(windowY - pressedAt);
		}

		/** Move the finger this far down (up when negative) from where it pressed, in one stroke. */
		void moveBy(float distance) {
			assertTrue("a drag past the list's edge autoscrolls without end under Robolectric",
					row.getTop() + distance >= 0 && row.getBottom() + distance <= list().getHeight());
			for (int step = 1; step <= STEPS; step++) {
				at = pressedAt + distance * step / STEPS;
				touch(MotionEvent.ACTION_MOVE);
			}
		}

		float grip() {
			return grip;
		}

		void letGo() {
			touch(MotionEvent.ACTION_UP);
			// Long enough for the dropped row to settle back into place, which is when the drop is saved.
			shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
		}

		private void touch(int action) {
			final MotionEvent event = MotionEvent.obtain(
					downTime,
					SystemClock.uptimeMillis(),
					action,
					x,
					at - windowY(list()),
					0);
			list().dispatchTouchEvent(event);
			event.recycle();
			shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));
		}
	}

	/** Perform an accessibility action on a family's row, as TalkBack does from its actions menu. */
	private boolean talkBack(DetailFamily family, int action) {
		final boolean handled = row(family).performAccessibilityAction(action, null);
		shadowOf(Looper.getMainLooper()).idle();
		return handled;
	}

	/**
	 * Draw the list once, as the screen would on its next frame. ItemTouchHelper places a dragged row under the finger while drawing, and Robolectric does not
	 * draw by itself, so without this a dragged row sits where layout put it.
	 */
	private void drawFrame() {
		final RecyclerView list = list();
		list.draw(new Canvas(Bitmap.createBitmap(list.getWidth(), list.getHeight(), Bitmap.Config.ARGB_8888)));
	}

	/** Where a view's top is in the window, wherever a scroll or a drag has moved it. */
	private static float windowY(View view) {
		final int[] location = new int[2];
		view.getLocationInWindow(location);
		return location[1];
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

	private View handle(DetailFamily family) {
		return row(family).findViewById(R.id.detail_drag_handle);
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

	private TextView previewTitle() {
		return controller.get().findViewById(R.id.notification_details_preview_title);
	}

	private TextView previewLine() {
		return controller.get().findViewById(R.id.notification_details_preview_line);
	}
}
