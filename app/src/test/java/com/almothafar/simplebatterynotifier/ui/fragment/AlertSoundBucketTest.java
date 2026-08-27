package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.ui.SettingsActivity;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The three sound pickers live on Notification Behaviour and each says which alerts it drives (#307).
 *
 * <p>One picker sets the sound for two or three notification channels — the critical one also covers high temperature, the warning one also covers fast-drain
 * and slow-charging — because {@code alertChannels()} collapses six channels onto three severity buckets. On the Alerts screen they sat inside per-event
 * categories, so that reach was invisible and changing the "Critical Alert" sound silently changed the high-temperature sound too.
 *
 * <p>What is pinned here is the part that regresses quietly. Dropping the {@code bucketSummary} attribute from a row breaks nothing and throws nothing: the
 * summary simply falls back to the bare sound name, which is exactly what it showed before this issue, so the screen still looks right while the information
 * the move existed to add is gone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AlertSoundBucketTest {

	private final Context context = ApplicationProvider.getApplicationContext();
	private ActivityController<SettingsActivity> controller;

	@After
	public void tearDown() {
		controller.close();
	}

	@Test
	public void everySoundPicker_namesTheAlertsItDrives() {
		showScreen(R.string.pref_category_behaviour);

		assertBucket(R.string._pref_key_notifications_alert_sound_ringtone, R.string.notifications_critical_sound_bucket);
		assertBucket(R.string._pref_key_notifications_warning_sound_ringtone, R.string.notifications_warning_sound_bucket);
		assertBucket(R.string._pref_key_notifications_full_sound_ringtone, R.string.notifications_default_sound_bucket);
	}

	/**
	 * The three sit together, in severity order. Presence alone would pass with them scattered through Appearance and Quiet Hours, which is the layout the
	 * issue set out to fix rather than a detail of it.
	 */
	@Test
	public void theThreePickersShareOneCategory_inSeverityOrder() {
		showScreen(R.string.pref_category_behaviour);

		final PreferenceCategory category = alertSoundsCategory();
		assertNotNull("there is no Alert Sounds category on Notification Behaviour", category);
		assertEquals("the Alert Sounds category holds something other than the three pickers", 3, category.getPreferenceCount());

		assertEquals(context.getString(R.string._pref_key_notifications_alert_sound_ringtone), category.getPreference(0).getKey());
		assertEquals(context.getString(R.string._pref_key_notifications_warning_sound_ringtone), category.getPreference(1).getKey());
		assertEquals(context.getString(R.string._pref_key_notifications_full_sound_ringtone), category.getPreference(2).getKey());
	}

	/**
	 * The counterpart to the assertions above: a picker left behind on Alerts would leave two rows writing the same preference, and the stale one would show
	 * a summary that stops matching the moment the other is used.
	 */
	@Test
	public void noSoundPickerIsLeftOnTheAlertsScreen() {
		showScreen(R.string.pref_category_alerts);

		assertNull(findPreference(R.string._pref_key_notifications_alert_sound_ringtone));
		assertNull(findPreference(R.string._pref_key_notifications_warning_sound_ringtone));
		assertNull(findPreference(R.string._pref_key_notifications_full_sound_ringtone));
	}

	/**
	 * Assert one picker's summary names the alerts its bucket drives. The sound's own name is deliberately not asserted — Robolectric resolves no ringtone,
	 * so it reads "Silent" here, and pinning that would test the shadow rather than this issue's change.
	 *
	 * @param keyRes    the picker's preference key resource
	 * @param bucketRes the bucket text it is expected to carry
	 */
	private void assertBucket(int keyRes, int bucketRes) {
		final Preference picker = findPreference(keyRes);
		assertNotNull("the sound picker is missing from Notification Behaviour", picker);

		final CharSequence summary = picker.getSummary();
		assertNotNull("the picker has no summary at all, so it cannot be naming its alerts", summary);
		assertTrue("the summary does not say what this picker drives: " + summary, summary.toString().contains(context.getString(bucketRes)));
	}

	/**
	 * @return the Alert Sounds category on the screen currently showing, or null when there is none
	 */
	private PreferenceCategory alertSoundsCategory() {
		final PreferenceScreen screen = SettingsScreen.current(controller).getPreferenceScreen();
		final String title = context.getString(R.string.pref_cat_title_alert_sounds);
		for (int i = 0; i < screen.getPreferenceCount(); i++) {
			if (screen.getPreference(i) instanceof final PreferenceCategory category && title.contentEquals(category.getTitle())) {
				return category;
			}
		}
		return null;
	}

	/**
	 * @param keyRes a preference key resource
	 * @return that preference on the screen currently showing, or null when it is not there
	 */
	private Preference findPreference(int keyRes) {
		return SettingsScreen.current(controller).findPreference(context.getString(keyRes));
	}

	/**
	 * Put one of the settings screens on the activity.
	 *
	 * @param categoryRes the category argument identifying which screen to inflate
	 */
	private void showScreen(int categoryRes) {
		controller = Robolectric.buildActivity(SettingsActivity.class).setup();
		SettingsScreen.show(controller, categoryRes);
	}
}
