package com.almothafar.simplebatterynotifier.ui;

import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.util.AppPrefs;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The gauge-corner theme toggle (#333). Two states over a three-state setting, so the parts worth pinning are the ones where that mismatch shows: which face
 * the button wears, and whether a tap owes the user the offer to go back to the system theme.
 * <p>
 * The face is driven by the resolved configuration rather than the stored choice, because on {@code THEME_SYSTEM} the stored value names the rule and says
 * nothing about the result — so both directions are exercised under {@code night} and {@code notnight} qualifiers rather than by writing a preference.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GaugeThemeToggleTest {

	@After
	public void restoreTheDefaultNightMode() {
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
	}

	private static ImageButton toggleOf(MainActivity activity) {
		return activity.findViewById(R.id.gaugeThemeButton);
	}

	@Test
	@Config(qualifiers = "notnight")
	public void inLightTheToggleOffersDark() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();

			assertEquals(activity.getString(R.string.gauge_theme_switch_to_dark), toggleOf(activity).getContentDescription());
		}
	}

	@Test
	@Config(qualifiers = "night")
	public void inDarkTheToggleOffersLight() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();

			assertEquals(activity.getString(R.string.gauge_theme_switch_to_light), toggleOf(activity).getContentDescription());
		}
	}

	/**
	 * The tap that leaves "System default" is the only one that owes an explanation, and the flag carrying it has to be written before the night mode is
	 * applied — that call recreates the activity, and anything not already in preferences goes with it.
	 */
	@Test
	@Config(qualifiers = "night")
	public void leavingTheSystemThemeRecordsTheOfferToGoBack() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();
			toggleOf(activity).performClick();

			assertEquals(AppPrefs.THEME_LIGHT, AppPrefs.themeChoice(ApplicationProvider.getApplicationContext()));
			assertTrue(AppPrefs.themeLeftSystem(ApplicationProvider.getApplicationContext()));
		}
	}

	/** Flipping between two explicit choices changes nothing about the system theme, so it says nothing. */
	@Test
	@Config(qualifiers = "night")
	public void flippingBetweenExplicitChoicesStaysQuiet() {
		AppPrefs.setThemeChoice(ApplicationProvider.getApplicationContext(), AppPrefs.THEME_DARK);

		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();
			toggleOf(activity).performClick();

			assertEquals(AppPrefs.THEME_LIGHT, AppPrefs.themeChoice(ApplicationProvider.getApplicationContext()));
			assertFalse(AppPrefs.themeLeftSystem(ApplicationProvider.getApplicationContext()));
		}
	}

	/**
	 * The glyph names where a tap goes, not where it already is: the screen is unmistakably light or dark on its own, so a sun in light mode would only repeat
	 * what the user can see. Asserted on the drawable rather than the description, because those two are set from the same flag and would agree even if the
	 * icons were the wrong way round.
	 */
	@Test
	@Config(qualifiers = "notnight")
	public void inLightTheGlyphIsTheMoonItWouldSwitchTo() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();

			assertEquals(R.drawable.ic_dark_mode, Shadows.shadowOf(toggleOf(activity).getDrawable()).getCreatedFromResId());
		}
	}

	@Test
	@Config(qualifiers = "night")
	public void inDarkTheGlyphIsTheSunItWouldSwitchTo() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();

			assertEquals(R.drawable.ic_light_mode, Shadows.shadowOf(toggleOf(activity).getDrawable()).getCreatedFromResId());
		}
	}

	/**
	 * While the app is following the system, the phone changing theme has to carry the toggle with it — an offer to "switch to dark" sitting on a screen that
	 * just went dark would be plainly wrong. Nothing in this class arranges that: the configuration change recreates the activity, and the face is read fresh
	 * from the new one. This pins that it stays true.
	 */
	@Test
	@Config(qualifiers = "notnight")
	public void theGlyphFollowsThePhoneWhileTheAppIsFollowingIt() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.setup();

			RuntimeEnvironment.setQualifiers("+night");
			controller.configurationChange();

			final MainActivity activity = controller.get();
			assertEquals(activity.getString(R.string.gauge_theme_switch_to_light), toggleOf(activity).getContentDescription());
			assertEquals(R.drawable.ic_light_mode, Shadows.shadowOf(toggleOf(activity).getDrawable()).getCreatedFromResId());
		}
	}

	/** Shown once: the launch that collects the offer also clears it, so it does not reappear at every start until the user acts. */
	@Test
	@Config(qualifiers = "notnight")
	public void theOfferIsCollectedOnceByTheNextLaunch() {
		AppPrefs.setThemeLeftSystem(ApplicationProvider.getApplicationContext(), true);

		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.setup();

			assertFalse(AppPrefs.themeLeftSystem(ApplicationProvider.getApplicationContext()));
		}
	}
}
