package com.almothafar.simplebatterynotifier;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.util.AppPrefs;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The half of the theme setting (#332) that {@code AppPrefsTest} cannot reach: the stored choice only survives force-stop and reboot because this class
 * re-applies it, and {@link AppCompatDelegate#setDefaultNightMode} writes to a static that dies with the process. Both the manifest registration and the
 * {@code onCreate} call are load-bearing — drop either and the app opens in the wrong theme, then visibly recreates itself.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BattWatchApplicationTest {

	@After
	public void restoreTheDefaultNightMode() {
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
	}

	/** Without {@code android:name} on the manifest's application tag the class below never runs, and nothing else would notice. */
	@Test
	public void manifestRegistersTheApplication() {
		assertTrue(ApplicationProvider.getApplicationContext() instanceof BattWatchApplication);
	}

	@Test
	public void appliesTheStoredThemeAsTheProcessStarts() {
		final BattWatchApplication application = ApplicationProvider.getApplicationContext();
		PreferenceManager.getDefaultSharedPreferences(application)
		                 .edit()
		                 .putString(application.getString(R.string._pref_key_theme), AppPrefs.THEME_DARK)
		                 .apply();

		application.onCreate();

		assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
	}
}
