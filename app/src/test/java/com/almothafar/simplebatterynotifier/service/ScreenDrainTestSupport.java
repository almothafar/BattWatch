package com.almothafar.simplebatterynotifier.service;

import android.content.Context;

import com.almothafar.simplebatterynotifier.model.BatteryDO;

/**
 * Lets tests outside this package record a screen observation at a time of their choosing. Robolectric leaves {@code System.currentTimeMillis()} on the
 * real clock for app code, so a test that needs an hour of screen-off behind it records that hour's start here and lets the code under test observe "now".
 */
public final class ScreenDrainTestSupport {

	private ScreenDrainTestSupport() {
		// Utility class - prevent instantiation
	}

	/** Records one battery sample at {@code atMillis}, as {@link BatteryRateTracker#record} would have at that time. */
	public static void recordAt(Context context, long atMillis, BatteryDO batteryDO, boolean screenOn) {
		ScreenDrainTracker.record(TransientState.prefs(context), atMillis, batteryDO, ScreenDrainTracker.ScreenState.of(screenOn));
	}
}
