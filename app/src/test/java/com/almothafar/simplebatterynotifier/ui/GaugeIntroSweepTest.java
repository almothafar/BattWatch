package com.almothafar.simplebatterynotifier.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.BatteryManager;

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
 * {@code animateLevelTo} always starts the ring at empty, which is right the first time the app opens and wrong every other time {@code initializeFirstValues}
 * runs — and it runs from {@code onPostResume}, so that is every return to the screen, plus every rotation and theme flip, since both recreate the activity.
 * Each spent a second showing a level the app already knew was wrong, with the theme toggle doing it while the user looked straight at the gauge.
 * <p>
 * The assertions read the gauge <em>immediately</em> after the event, before the looper advances the animator. That is the window the defect lives in: a
 * version that sweeps leaves the ring reading 0 with its animator still running, where a fixed one is already showing the level and animating nothing.
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
		try (final ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			final MainActivity opened = controller.setup().get();
			assertEquals("the gauge never reached the seeded level", SEEDED_LEVEL, gaugeOf(opened).getLevel());

			RuntimeEnvironment.setQualifiers("+night");
			controller.configurationChange();

			assertEquals("the ring restarted from empty", SEEDED_LEVEL, gaugeOf(controller.get()).getLevel());
		}
	}

	/**
	 * The level alone cannot pin this one. A recreate drives {@code initializeFirstValues()} more than once, so a version that sweeps on the first pass still
	 * lands on the right number by the last, and the end state looks identical. What differs is that a sweep was started and is still running — which is
	 * exactly what the user sees as the ring dropping to empty and climbing back.
	 */
	@Test
	public void noSweepIsEvenStartedWhenTheActivityIsRecreated() {
		try (final ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.setup();

			RuntimeEnvironment.setQualifiers("+night");
			controller.configurationChange();

			assertFalse("a sweep was started on a recreate", gaugeOf(controller.get()).isAnimatingLevel());
		}
	}

	/**
	 * The trigger the issue missed, and the one with no recreate behind it: the settings launcher's result callback is the second caller of
	 * {@code initializeFirstValues()}, so the {@code savedInstanceState} seed cannot help here. Only the latch can, by remembering that this instance has
	 * already had its opening sweep.
	 */
	@Test
	public void theGaugeDoesNotSweepAgainOnTheWayBackFromSettings() {
		try (final ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
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
		try (final ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.setup();

			RuntimeEnvironment.setQualifiers("+land");
			controller.configurationChange();

			assertEquals("the ring restarted from empty", SEEDED_LEVEL, gaugeOf(controller.get()).getLevel());
		}
	}

	/**
	 * The other half of the rule, and the one a fix for this bug can quietly destroy: a cold start must still sweep. Nothing else here would notice a version
	 * that simply never animates, since every assertion above is about <em>not</em> sweeping.
	 * <p>
	 * Robolectric surfaces no battery level during {@code onCreate}, so a freshly created activity animates to zero and its level says nothing afterwards. The
	 * sweep is caught in flight instead, which is why the lifecycle stops one step short of the {@code setup()} used everywhere else: {@code visible()} idles
	 * the looper, and an idle runs a Robolectric animator to completion, leaving a finished sweep indistinguishable from one that never started.
	 */
	@Test
	public void aColdStartStillSweepsUpFromEmpty() {
		try (final ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
			controller.create().start().resume();

			assertTrue("no opening sweep on a cold start", gaugeOf(controller.get()).isAnimatingLevel());
		}
	}
}
