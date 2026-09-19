package com.almothafar.simplebatterynotifier.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.almothafar.simplebatterynotifier.service.PowerConnectionService;

import static java.util.Objects.isNull;

/**
 * Brings {@link PowerConnectionService} back at the two moments monitoring is down with no screen open to restart it.
 * <p>
 * A reboot is the obvious one. The other is the app updating itself (#348): replacing a package force-stops it, and a force-stop clears the pending
 * {@code START_STICKY} restart, so the sticky return in {@code PowerConnectionService.onStartCommand} does not cover an update the way it covers a
 * process kill. Before this receiver took the second action, the only other thing that starts the service is {@code MainActivity}, which means monitoring
 * stayed down after every Play update until the user happened to open the app — silently, since the ongoing notification goes away with it.
 * <p>
 * Nothing in the app lets a user turn monitoring off, so both actions start the service unconditionally, exactly as {@code MainActivity} does.
 * <p>
 * Started as a foreground service rather than with {@code startService}: both broadcasts arrive with the app in the background, where a plain start throws
 * {@code BackgroundServiceStartNotAllowedException} on Android 8+. The promotion itself is allowed because
 * <a href="https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start">Android exempts both of these actions</a> from the
 * Android 12+ restriction on starting a foreground service from the background.
 */
public class MonitoringRestartReceiver extends BroadcastReceiver {

	/**
	 * Starts battery monitoring when the device has booted or the app has just been replaced.
	 *
	 * @param context the context the receiver is running in
	 * @param intent  the broadcast being received; any action other than the two this receiver is registered for is ignored
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		if (isNull(intent)) {
			return;
		}

		// Checked rather than assumed from the manifest filter, so a spoofed or mis-registered broadcast cannot start the service.
		final String action = intent.getAction();
		if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
			ContextCompat.startForegroundService(context, new Intent(context, PowerConnectionService.class));
		}
	}
}
