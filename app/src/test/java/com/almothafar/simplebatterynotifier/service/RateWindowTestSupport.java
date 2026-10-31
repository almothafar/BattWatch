package com.almothafar.simplebatterynotifier.service;

import android.content.Context;

import java.util.List;

/**
 * Lets tests outside this package give the 10-minute rate window a rate. Robolectric leaves {@code System.currentTimeMillis()} on the real clock for app
 * code, so the minutes of readings a rate needs are written here, in {@link BatteryRateTracker}'s persisted form, as if they had been recorded.
 */
public final class RateWindowTestSupport {

	private RateWindowTestSupport() {
		// Utility class - prevent instantiation
	}

	/**
	 * Fill the rate window as minutes of readings would: two levels apart over six minutes, 20%/h, and no current in either. Six minutes keeps clear of both
	 * the three the level-over-time rate needs and the ten the window keeps; a screen that records its own reading as it opens, as the details table does,
	 * stretches it to seven, 17%/h. The keys are {@code BatteryRateTracker}'s own; each test that fills the window checks the rate first, so a change to
	 * them fails the test instead of leaving it to pass on an empty window.
	 */
	public static void fill(Context context, boolean charging, int fromLevel, int toLevel) {
		final long now = System.currentTimeMillis();
		final List<BatteryRateTracker.Sample> samples = List.of(
				new BatteryRateTracker.Sample(now - 420_000, fromLevel, BatteryRateTracker.PROPERTY_UNSUPPORTED),
				new BatteryRateTracker.Sample(now - 60_000, toLevel, BatteryRateTracker.PROPERTY_UNSUPPORTED));
		TransientState.prefs(context)
		              .edit()
		              .putString("_battery_rate_samples", BatteryRateTracker.serializeSamples(samples))
		              .putInt("_battery_rate_charging", charging ? 1 : 0)
		              .commit();
	}
}
