package com.almothafar.simplebatterynotifier;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.almothafar.simplebatterynotifier.util.AppPrefs;

/**
 * Applies the stored theme choice (#332) as the process starts.
 * <p>
 * This exists because the theme and the app language persist differently. {@code setApplicationLocales} is stored for us by AndroidX, so the language picker
 * can be a non-persistent preference that only reflects it. {@link AppCompatDelegate#setDefaultNightMode} writes to a static instead, which dies with the
 * process — so the theme is a normal persisted preference and something has to re-apply it on every start. Here is the only place early enough: the process
 * can be started by the monitoring service or a receiver rather than by an activity, and the mode must be set before the first one inflates or it would open
 * in the wrong theme and then visibly recreate itself.
 */
public class BattWatchApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		AppCompatDelegate.setDefaultNightMode(AppPrefs.themeMode(this));
	}
}
