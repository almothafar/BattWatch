package com.almothafar.simplebatterynotifier.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.ui.widget.HorseshoeProgressBar;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The opening sweep belongs to a cold start and to nothing else (#339).
 * <p>
 * {@code animateLevelTo} always starts the ring at empty, which is right the first time the app opens and wrong every other time it is called — and it was
 * called on three of them: the return from Settings, a rotation, and the theme toggle, which recreates the activity through {@code setDefaultNightMode}. Each
 * spent a second showing a level the app already knew was wrong, with the theme toggle doing it while the user looked straight at the gauge.
 * <p>
 * The recreate assertions read the level <em>immediately</em> after the configuration change, before the looper advances the animator. That is the window the
 * defect lives in: mutating the fix away drops the reading to 0 there, because a fresh sweep had begun from empty.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GaugeIntroSweepTest {

	private static final int SEEDED_LEVEL = 72;

	/**
	 * Robolectric does not surface the sticky battery broadcast during {@code onCreate}, so the reading only lands once the activity is resumed. Every
	 * assertion below therefore works from an activity that has been through a full {@code setup()} and is showing a real level.
	 */
	@Before
	public void seedBatteryLevel() {
		final Intent battery = new Intent(Intent.ACTION_BATTERY_CHANGED);
		battery.putExtra(BatteryManager.EXTRA_LEVEL, SEEDED_LEVEL);
		battery.putExtra(BatteryManager.EXTRA_SCALE, 100);
		battery.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING);
		battery.putExtra(BatteryManager.EXTRA_PRESENT, true);
		battery.putExtra(BatteryManager.EXTRA_TEMPERATURE, 300);
		((Application) ApplicationProvider.getApplicationContext()).sendStickyBroadcast(battery);
	}

	private static HorseshoeProgressBar gaugeOf(MainActivity activity) {
		return activity.findViewById(R.id.batteryPercentage);
	}

	/** The case the user sees most often, because the toggle that causes it sits on the gauge itself. */
	@Test
	public void theGaugeKeepsItsLevelWhenTheThemeFlips() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity opened = controller.setup().get();
			assertEquals("the gauge never reached the seeded level", SEEDED_LEVEL, gaugeOf(opened).getLevel());

			RuntimeEnvironment.setQualifiers("+night");
			controller.configurationChange();

			assertEquals("the ring restarted from empty", SEEDED_LEVEL, gaugeOf(controller.get()).getLevel());
		}
	}

	/**
	 * The level alone cannot pin this one. {@code initializeFirstValues()} runs <em>twice</em> on a recreate — once from {@code onCreate}, once more as the
	 * activity-result launcher re-delivers — so a version that sweeps on the first call still lands on the right number by the second, and the end state looks
	 * identical. What differs is that a sweep was started and is still running, which is exactly what the user sees as the ring dropping to empty.
	 */
	@Test
	public void noSweepIsEvenStartedWhenTheActivityIsRecreated() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.setup();

			RuntimeEnvironment.setQualifiers("+night");
			controller.configurationChange();

			assertFalse("a sweep was started on a recreate", gaugeOf(controller.get()).isAnimatingLevel());
		}
	}

	/**
	 * The most frequent trigger of the three, and the one the issue missed: {@code initializeFirstValues()} has a second caller, the activity-result callback
	 * that fires on the way back from Settings. No recreate is involved there, so the {@code savedInstanceState} seed cannot help — only the latch, which
	 * remembers that this instance has already had its opening sweep.
	 */
	@Test
	public void theGaugeDoesNotSweepAgainOnTheWayBackFromSettings() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity activity = controller.setup().get();
			final ShadowActivity shadow = Shadows.shadowOf(activity);

			assertTrue("the settings menu item did not fire", shadow.clickMenuItem(R.id.action_settings));
			shadow.receiveResult(shadow.getNextStartedActivity(), Activity.RESULT_OK, null);

			assertEquals("the ring restarted from empty", SEEDED_LEVEL, gaugeOf(activity).getLevel());
			assertFalse("a sweep was started on the way back from Settings", gaugeOf(activity).isAnimatingLevel());
		}
	}

	/** Same path, a different configuration: the fix must be about recreating at all, not about the theme in particular. */
	@Test
	public void theGaugeKeepsItsLevelThroughARotation() {
		try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.setup();

			RuntimeEnvironment.setQualifiers("+land");
			controller.configurationChange();

			assertEquals("the ring restarted from empty", SEEDED_LEVEL, gaugeOf(controller.get()).getLevel());
		}
	}

	/**
	 * The sweep itself is worth keeping, so this pins that it still starts from empty and still arrives — the opening flourish is the one place it earns its
	 * second. Asserted on the widget rather than through the activity, because Robolectric reports no battery level during {@code onCreate}, which would make
	 * a cold-start assertion pass on a gauge that animated to zero.
	 */
	@Test
	public void theOpeningSweepStillRunsFromEmptyToTheLevel() {
		final HorseshoeProgressBar gauge = new HorseshoeProgressBar(ApplicationProvider.getApplicationContext());

		gauge.animateLevelTo(SEEDED_LEVEL, null);
		assertEquals("the sweep did not start from empty", 0, gauge.getLevel());
		assertTrue("a sweep in flight does not report itself", gauge.isAnimatingLevel());

		Shadows.shadowOf(Looper.getMainLooper()).idle();
		assertEquals("the sweep did not arrive", SEEDED_LEVEL, gauge.getLevel());
		assertFalse("a finished sweep still reports itself", gauge.isAnimatingLevel());
	}
}
