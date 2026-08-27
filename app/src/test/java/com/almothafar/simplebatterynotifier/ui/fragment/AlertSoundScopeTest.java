package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The three sound pickers live on Notification Behaviour and each says what it drives (#307).
 *
 * <p>One picker sets the sound for two or three notification channels — the critical one also covers overheating, the warning one also covers fast-drain and
 * slow-charge — because {@code alertChannels()} collapses six channels onto three severity buckets. On the Alerts screen they sat inside per-event categories,
 * so that scope was invisible and changing the "Critical Alert" sound silently changed the overheat sound too.
 *
 * <p>What is pinned here is the part that regresses quietly. Dropping the {@code scopeSummary} attribute from a row breaks nothing and throws nothing: the
 * summary simply falls back to the bare sound name, which is exactly what it showed before this issue, so the screen still looks right while the information
 * the move existed to add is gone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AlertSoundScopeTest {

	private final Context context = ApplicationProvider.getApplicationContext();
	private ActivityController<SettingsActivity> controller;

	@After
	public void tearDown() {
		controller.close();
	}

	@Test
	public void everySoundPicker_namesTheAlertsItDrives() {
		showScreen(R.string.pref_category_behaviour);

		assertScope(R.string._pref_key_notifications_alert_sound_ringtone, R.string.notifications_alert_sound_scope);
		assertScope(R.string._pref_key_notifications_warning_sound_ringtone, R.string.notifications_warning_sound_scope);
		assertScope(R.string._pref_key_notifications_full_sound_ringtone, R.string.notifications_full_sound_scope);
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
	 * Assert one picker's summary carries its bucket's scope. The sound's own name is deliberately not asserted — Robolectric resolves no ringtone, so it
	 * reads "Silent" here, and pinning that would test the shadow rather than this issue's change.
	 *
	 * @param keyRes   the picker's preference key resource
	 * @param scopeRes the scope text it is expected to carry
	 */
	private void assertScope(int keyRes, int scopeRes) {
		final Preference picker = findPreference(keyRes);
		assertNotNull("the sound picker is missing from Notification Behaviour", picker);

		final CharSequence summary = picker.getSummary();
		assertNotNull("the picker has no summary at all, so it cannot be naming its scope", summary);
		assertTrue("the summary does not say what this picker drives: " + summary,
				summary.toString().contains(context.getString(scopeRes)));
	}

	/**
	 * @param keyRes a preference key resource
	 * @return that preference on the screen currently showing, or null when it is not there
	 */
	private Preference findPreference(int keyRes) {
		final Fragment fragment = controller.get().getSupportFragmentManager().findFragmentById(R.id.settings_container);
		assertTrue("the settings screen is not showing", fragment instanceof GenericPreferenceFragment);
		return ((GenericPreferenceFragment) fragment).findPreference(context.getString(keyRes));
	}

	/**
	 * Put one of the settings screens on the activity.
	 *
	 * @param categoryRes the category argument identifying which screen to inflate
	 */
	private void showScreen(int categoryRes) {
		controller = Robolectric.buildActivity(SettingsActivity.class).setup();

		final GenericPreferenceFragment fragment = new GenericPreferenceFragment();
		final Bundle args = new Bundle();
		args.putString("category", context.getString(categoryRes));
		fragment.setArguments(args);
		controller.get()
		          .getSupportFragmentManager()
		          .beginTransaction()
		          .replace(R.id.settings_container, fragment)
		          .commitNow();
	}
}
