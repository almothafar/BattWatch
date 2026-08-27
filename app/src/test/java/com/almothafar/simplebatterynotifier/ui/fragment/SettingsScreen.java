package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.ui.SettingsActivity;

import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertTrue;

/**
 * Putting a settings screen on a {@link SettingsActivity} and getting it back, for the tests that exercise the preference screens themselves.
 * <p>
 * Extracted in #307, where a second test needed the same two steps the first already had. Production never builds these arguments in Java — the category
 * comes from an {@code <extra>} on each row of {@code pref_headers_root.xml} — so a test that wants a specific screen has to assemble what the framework
 * would have passed, and doing that in one place keeps the two tests agreeing on what "showing the Alerts screen" means.
 */
final class SettingsScreen {

	private SettingsScreen() {
	}

	/**
	 * Inflate one of the preference screens onto the activity.
	 *
	 * @param controller  the activity under test, already set up
	 * @param categoryRes the {@code pref_category_*} resource naming which screen to inflate
	 */
	static void show(ActivityController<SettingsActivity> controller, int categoryRes) {
		final Context context = ApplicationProvider.getApplicationContext();
		final GenericPreferenceFragment fragment = new GenericPreferenceFragment();
		final Bundle args = new Bundle();
		args.putString(GenericPreferenceFragment.ARG_CATEGORY, context.getString(categoryRes));
		fragment.setArguments(args);
		controller.get()
		          .getSupportFragmentManager()
		          .beginTransaction()
		          .replace(R.id.settings_container, fragment)
		          .commitNow();
	}

	/**
	 * @param controller the activity under test
	 * @return the preference fragment the activity currently holds
	 */
	static GenericPreferenceFragment current(ActivityController<SettingsActivity> controller) {
		final Fragment fragment = controller.get().getSupportFragmentManager().findFragmentById(R.id.settings_container);
		assertTrue("no settings screen is showing", fragment instanceof GenericPreferenceFragment);
		return (GenericPreferenceFragment) fragment;
	}
}
