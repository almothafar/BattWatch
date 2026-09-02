package com.almothafar.simplebatterynotifier.ui;

import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

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

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The gauge-corner theme toggle (#333). Two states over a three-state setting, so the parts worth pinning are the ones where that mismatch shows: which face
 * the button wears, and whether a tap owes the user the offer to go back to the system theme.
 * <p>
 * The face is driven by the resolved configuration rather than the stored choice, because on {@code THEME_SYSTEM} the stored value names the rule and says
 * nothing about the result — so both directions are exercised under {@code night} and {@code notnight} qualifiers rather than by writing a preference.
 * <p>
 * One thing here is deliberately not covered, so nobody spends an afternoon rediscovering it: that the appearance is read from the <em>activity's</em>
 * configuration rather than the application context. Robolectric hands both the same {@code Resources} instance, so the two reads are the same call and no
 * test in this file can separate them. See {@link #theFaceFollowsAnExplicitNightModeOverALightEnvironment()}, which covers the case that motivates the
 * distinction without being able to pin the distinction itself.
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

	/** The "staying light/dark" line, or null when no offer is on screen. */
	private static TextView offerText(MainActivity activity) {
		return activity.findViewById(com.google.android.material.R.id.snackbar_text);
	}

	/** The "Match my phone" button on that offer, or null when no offer is on screen. */
	private static Button offerAction(MainActivity activity) {
		return activity.findViewById(com.google.android.material.R.id.snackbar_action);
	}

	/** A snackbar reaches the screen through the manager's handler, so nothing is attached until the main looper has run. */
	private static void settle() {
		Shadows.shadowOf(Looper.getMainLooper()).idle();
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
	 * icons were the wrong way round — and because the layout carries only a {@code tools:src}, so the drawable is null until the activity has set it.
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
	 * The EMUI-shaped case: the app is dark because {@code setDefaultNightMode} says so, while the environment around it is still light. That is how dark
	 * arrives on the Mate 10 Pro — EMUI never propagates night to the app config — and it is the path the qualifier-driven tests above never take, since a
	 * qualifier makes the whole environment dark and the night mode redundant.
	 * <p>
	 * It does <em>not</em> pin that the appearance is read from the activity rather than the application context, which is what the production code's own
	 * comment is careful about. Robolectric hands both the same {@code Resources} instance — measured, {@code activity.getResources() ==
	 * application.getResources()} — so mutating the read to {@code getApplicationContext()} passes every test in this class. No unit test can catch that one;
	 * only a device with an un-nighted app config can, which is the Mate 10 Pro.
	 */
	@Test
	@Config(qualifiers = "notnight")
	public void theFaceFollowsAnExplicitNightModeOverALightEnvironment() {
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

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

	/** The offer names the appearance the user is keeping and the destination its action goes to — the two halves a user reads before deciding. */
	@Test
	@Config(qualifiers = "night")
	public void theOfferNamesTheAppearanceItIsKeeping() {
		AppPrefs.setThemeLeftSystem(ApplicationProvider.getApplicationContext(), true);

		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();
			settle();

			assertNotNull("no offer on screen", offerText(activity));
			assertEquals(activity.getString(R.string.theme_staying_dark), offerText(activity).getText().toString());
			assertEquals(activity.getString(R.string.theme_match_phone_action), offerAction(activity).getText().toString());
		}
	}

	/** The action is the whole point of the bar: it has to put the stored choice back to system, so the Settings picker agrees with it afterwards. */
	@Test
	@Config(qualifiers = "notnight")
	public void matchMyPhoneHandsTheThemeBackToTheSystem() {
		AppPrefs.setThemeChoice(ApplicationProvider.getApplicationContext(), AppPrefs.THEME_DARK);
		AppPrefs.setThemeLeftSystem(ApplicationProvider.getApplicationContext(), true);

		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();
			settle();

			assertNotNull("no offer to act on", offerAction(activity));
			offerAction(activity).performClick();

			assertEquals(AppPrefs.THEME_SYSTEM, AppPrefs.themeChoice(ApplicationProvider.getApplicationContext()));
			assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
		}
	}

	/**
	 * Spending the offer on a bar nobody saw is the failure this pins. Clearing the flag as the bar goes up looks equivalent and is not: a rotation inside the
	 * window tears the bar down with the activity, and the offer — the only route back to the system theme outside Settings — would be gone for good.
	 */
	@Test
	@Config(qualifiers = "notnight")
	public void theOfferSurvivesARotationInsideItsWindow() {
		AppPrefs.setThemeLeftSystem(ApplicationProvider.getApplicationContext(), true);

		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.setup();
			settle();
			assertTrue("spent before anyone could act on it", AppPrefs.themeLeftSystem(ApplicationProvider.getApplicationContext()));

			RuntimeEnvironment.setQualifiers("+land");
			controller.configurationChange();
			settle();

			assertNotNull("the offer did not come back with the recreated activity", offerText(controller.get()));
		}
	}

	/** Shown once: the bar that has had its time on screen spends the offer, so it does not reappear at every start until the user acts. */
	@Test
	@Config(qualifiers = "notnight")
	public void theOfferIsSpentOnceItHasHadItsTimeOnScreen() {
		AppPrefs.setThemeLeftSystem(ApplicationProvider.getApplicationContext(), true);

		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.setup();
			Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));

			assertFalse(AppPrefs.themeLeftSystem(ApplicationProvider.getApplicationContext()));
		}
	}
}
