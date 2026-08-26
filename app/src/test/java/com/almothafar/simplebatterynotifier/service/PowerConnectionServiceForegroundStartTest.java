package com.almothafar.simplebatterynotifier.service;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;

import androidx.core.app.ServiceCompat;

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
 * {@link ServiceCompat} is mocked statically so the refusal can be provoked without a real platform
 * decision; the assertions are about what {@code onCreate} does with it, not about when Android throws.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PowerConnectionServiceForegroundStartTest {

	@Test
	public void promotionRefused_stopsServiceInsteadOfCrashing() {
		final ServiceController<PowerConnectionService> controller = Robolectric.buildService(PowerConnectionService.class);

		try (MockedStatic<ServiceCompat> serviceCompat = mockStatic(ServiceCompat.class)) {
			serviceCompat.when(() -> ServiceCompat.startForeground(any(Service.class), anyInt(), any(Notification.class), anyInt()))
			             .thenThrow(new ForegroundServiceStartNotAllowedException("refused by test"));

			// The bug was that this threw: an uncaught exception out of onCreate takes the process down.
			controller.create();

			final ShadowService shadow = shadowOf(controller.get());
			assertTrue("service should stop itself when the promotion is refused", shadow.isStoppedBySelf());
		}
	}

	@Test
	public void promotionAllowed_keepsServiceRunning() {
		final ServiceController<PowerConnectionService> controller = Robolectric.buildService(PowerConnectionService.class);

		try (MockedStatic<ServiceCompat> serviceCompat = mockStatic(ServiceCompat.class)) {
			controller.create();

			serviceCompat.verify(() -> ServiceCompat.startForeground(any(Service.class), anyInt(), any(Notification.class), anyInt()));

			final ShadowService shadow = shadowOf(controller.get());
			assertFalse("service should keep running when the promotion succeeds", shadow.isStoppedBySelf());
		}
	}
}
