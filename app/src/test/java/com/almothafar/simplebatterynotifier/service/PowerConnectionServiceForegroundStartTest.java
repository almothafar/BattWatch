package com.almothafar.simplebatterynotifier.service;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;
import android.content.Context;

import androidx.core.app.ServiceCompat;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.util.AppPrefs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.Shadows.shadowOf;

/**
 * Robolectric + Mockito tests for {@link PowerConnectionService}'s foreground promotion (#295).
 * <p>
 * {@code onStartCommand} returns {@code START_STICKY}, so the system recreates the service by itself after
 * killing the process — a background start with none of the exemptions that allow promotion to foreground.
 * Android 12+ refuses it with {@link ForegroundServiceStartNotAllowedException}, which used to escape
 * {@code onCreate} and crash the process. The service must stop instead.
 * <p>
 * Stopping is not the whole answer, because monitoring is then off with nothing on screen to say so: the refusal is also recorded in {@link AppPrefs} for
 * {@code MainActivity} to explain at the next launch (#302), and a promotion that succeeds must leave nothing to explain.
 * <p>
 * {@link ServiceCompat} is mocked statically so the refusal can be provoked without a real platform
 * decision; the assertions are about what {@code onCreate} does with it, not about when Android throws.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PowerConnectionServiceForegroundStartTest {

	@Test
	public void promotionRefused_stopsServiceAndRecordsTheInterruption() {
		final ServiceController<PowerConnectionService> controller = Robolectric.buildService(PowerConnectionService.class);
		final Context context = ApplicationProvider.getApplicationContext();

		try (MockedStatic<ServiceCompat> serviceCompat = mockStatic(ServiceCompat.class)) {
			serviceCompat.when(() -> ServiceCompat.startForeground(any(Service.class), anyInt(), any(Notification.class), anyInt()))
			             .thenThrow(new ForegroundServiceStartNotAllowedException("refused by test"));

			// The bug was that this threw: an uncaught exception out of onCreate takes the process down.
			controller.create();

			final ShadowService shadow = shadowOf(controller.get());
			assertTrue("service should stop itself when the promotion is refused", shadow.isStoppedBySelf());
			assertTrue("the refusal should be left for MainActivity to explain (#302)", AppPrefs.monitoringStopped(context));
		}
	}

	@Test
	public void promotionAllowed_keepsServiceRunningAndLeavesNothingToExplain() {
		final ServiceController<PowerConnectionService> controller = Robolectric.buildService(PowerConnectionService.class);
		final Context context = ApplicationProvider.getApplicationContext();

		try (MockedStatic<ServiceCompat> serviceCompat = mockStatic(ServiceCompat.class)) {
			controller.create();

			serviceCompat.verify(() -> ServiceCompat.startForeground(any(Service.class), anyInt(), any(Notification.class), anyInt()));

			final ShadowService shadow = shadowOf(controller.get());
			assertFalse("service should keep running when the promotion succeeds", shadow.isStoppedBySelf());
			assertFalse("monitoring was never interrupted, so there is nothing to tell the user", AppPrefs.monitoringStopped(context));
		}
	}
}
