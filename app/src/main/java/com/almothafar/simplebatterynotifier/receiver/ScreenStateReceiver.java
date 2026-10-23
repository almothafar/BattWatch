package com.almothafar.simplebatterynotifier.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.almothafar.simplebatterynotifier.service.ScreenDrainTracker;
import com.almothafar.simplebatterynotifier.service.SystemService;

import static java.util.Objects.isNull;

/**
 * Feeds the screen-on / screen-off drain split ({@link ScreenDrainTracker}, #193) at the moment the screen toggles. Battery broadcasts can be tens of minutes
 * apart in doze, so on their own they could not say when inside such a gap the screen changed state; this broadcast arrives at the toggle itself.
 * <p>
 * {@code ACTION_SCREEN_ON} and {@code ACTION_SCREEN_OFF} can only be received by a receiver registered at runtime, never one declared in the manifest, which
 * is why the long-lived {@code PowerConnectionService} registers this beside its battery receivers. Delivered on the main thread, like them.
 */
public class ScreenStateReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = isNull(intent) ? null : intent.getAction();
		if (!Intent.ACTION_SCREEN_ON.equals(action) && !Intent.ACTION_SCREEN_OFF.equals(action)) {
			return;
		}
		// The tracker reads the screen state itself, so a toggle that has already flipped back by the time this runs is recorded as the screen now is, not as
		// the broadcast said.
		ScreenDrainTracker.onScreenChanged(context, SystemService.getBatteryInfo(context));
	}
}
