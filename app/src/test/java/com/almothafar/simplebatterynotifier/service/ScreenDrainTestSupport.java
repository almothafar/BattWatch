package com.almothafar.simplebatterynotifier.service;

import android.content.Context;

import com.almothafar.simplebatterynotifier.model.BatteryDO;

/**
 * Lets tests outside this package record a screen observation at a time of their choosing. Robolectric leaves {@code System.currentTimeMillis()} on the
 * real clock for app code, so a test that needs an hour of screen-off behind it records that hour here, as the monitoring service would have seen it.
 */
public final class ScreenDrainTestSupport {

	private ScreenDrainTestSupport() {
		// Utility class - prevent instantiation
	}

	/** Records one battery broadcast at {@code atMillis}, as {@link ScreenDrainTracker#onBatteryChanged} would have at that time. */
	public static void recordAt(Context context, long atMillis, BatteryDO batteryDO, boolean screenOn) {
		ScreenDrainTracker.batteryChangedAt(TransientState.prefs(context), atMillis, batteryDO, ScreenDrainTracker.ScreenState.of(screenOn));
	}
}
