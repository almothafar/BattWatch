package com.almothafar.simplebatterynotifier.service;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.ServiceCompat;
import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver;
import com.almothafar.simplebatterynotifier.receiver.PowerConnectionReceiver;
import com.almothafar.simplebatterynotifier.receiver.ScreenStateReceiver;
import com.almothafar.simplebatterynotifier.util.AppPrefs;

import java.util.Locale;

import static java.util.Objects.isNull;

/**
 * Service to register battery monitoring receivers.
 * Registers PowerConnectionReceiver, BatteryLevelReceiver and ScreenStateReceiver on service creation.
 */
public class PowerConnectionService extends Service {
	private static final String TAG = "PowerConnectionService";

	private PowerConnectionReceiver powerConnectionReceiver;
	private BatteryLevelReceiver batteryLevelReceiver;
	private ScreenStateReceiver screenStateReceiver;

	@Override
	public IBinder onBind(final Intent intent) {
		return null; // This is a started service, not a bound service
	}

	@Override
	public void onCreate() {
		super.onCreate();
		// The active language, stated once where a log is guaranteed to start: this service is the
		// long-lived component and it starts on boot. Worth having explicitly because locale changes
		// behaviour that is otherwise invisible in a log — most of all the digit shapes CLDR picks for
		// region-bearing Arabic locales, which is what corrupted a stored value in #154/#241. Reading
		// it off a formatted number instead would be guesswork: bare "ar" formats Western digits too.
		Log.i(TAG, "Starting with locale: " + Locale.getDefault().toLanguageTag());
		// Promote to foreground first so the OS keeps the process (and our receivers) alive on Android 8+.
		startForegroundWithStatus();
		registerReceivers();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceivers();
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
		// Re-assert the foreground notification (e.g. after a START_STICKY restart delivers a null intent).
		startForegroundWithStatus();
		return START_STICKY;
	}

	/**
	 * Promote this service to the foreground with the persistent battery-status notification.
	 * <p>
	 * Required on Android 8+: a plain background service (and its runtime-registered battery
	 * receivers) is reaped shortly after the app leaves the foreground. The ongoing notification
	 * keeps monitoring alive so alerts are delivered while the app is closed.
	 * <p>
	 * The promotion can be refused. {@code onStartCommand} returns {@code START_STICKY}, so the system recreates this service on its own after killing the
	 * process, and that restart carries none of the exemptions that let a background start promote to foreground (#295). Android 12+ answers with
	 * {@link ForegroundServiceStartNotAllowedException}, which is fatal if it escapes {@code onCreate}. Monitoring is already down at that point, so the
	 * recovery is to stop rather than to crash: the next launch of {@code MainActivity} starts the service again from the foreground, where it is allowed. The
	 * refusal is recorded on the way out so that launch can also tell the user monitoring was interrupted, and how to make it less likely (#302).
	 */
	private void startForegroundWithStatus() {
		final BatteryDO batteryDO = SystemService.getBatteryInfo(this);
		final Notification notification = NotificationService.buildOngoingNotification(this, batteryDO);

		// The specialUse FGS type only exists from Android 14 (API 34); pass 0 on older versions.
		final int serviceType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
		                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
		                        : 0;
		final int id = NotificationService.getOngoingNotificationId();

		// Below Android 12 the promotion cannot be refused, so there is nothing to catch — and the exception
		// class does not exist on those versions to catch either.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			ServiceCompat.startForeground(this, id, notification, serviceType);
			return;
		}

		try {
			ServiceCompat.startForeground(this, id, notification, serviceType);
		} catch (ForegroundServiceStartNotAllowedException e) {
			Log.w(TAG, "Foreground start refused from the background, stopping until the app is opened again", e);
			// Monitoring is off from here, and a service has no way to say so. Leave a mark for MainActivity to explain it at the next launch (#302).
			AppPrefs.setMonitoringStopped(this, true);
			stopSelf();
		}
	}

	/**
	 * Register the battery and screen monitoring receivers.
	 * Initializes the current plugged state to avoid unnecessary triggers on the first battery change event.
	 * <p>
	 * The screen receiver feeds the screen-on / screen-off drain split (#193). Until it is registered, screen toggles go unseen, so the split first drops the
	 * stretch it was in the middle of when monitoring last stopped rather than credit it to one state.
	 */
	private void registerReceivers() {
		final Intent batteryStatus = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		final int plugged = isNull(batteryStatus) ? -1 : batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

		powerConnectionReceiver = new PowerConnectionReceiver();
		PowerConnectionReceiver.setCurrentState(plugged);

		batteryLevelReceiver = new BatteryLevelReceiver();

		final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(powerConnectionReceiver, filter);
		registerReceiver(batteryLevelReceiver, filter);

		ScreenDrainTracker.onMonitoringStarted(this, SystemService.getBatteryInfo(this, batteryStatus));
		screenStateReceiver = new ScreenStateReceiver();
		final IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(screenStateReceiver, screenFilter);
	}

	/**
	 * Unregister the monitoring receivers to prevent memory leaks
	 */
	private void unregisterReceivers() {
		unregister(powerConnectionReceiver);
		powerConnectionReceiver = null;
		unregister(batteryLevelReceiver);
		batteryLevelReceiver = null;
		unregister(screenStateReceiver);
		screenStateReceiver = null;
	}

	private void unregister(BroadcastReceiver receiver) {
		if (isNull(receiver)) {
			return;
		}
		try {
			unregisterReceiver(receiver);
		} catch (IllegalArgumentException e) {
			// Nothing to undo: the receiver is already gone, which is the state this was asked to reach.
			Log.w(TAG, "Receiver was already unregistered: " + receiver.getClass().getSimpleName(), e);
		}
	}
}
