package com.almothafar.simplebatterynotifier.receiver;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.service.PowerConnectionService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Checks that monitoring restarts after a reboot and after the app updates, the fix for "monitoring does not restart after the app updates" (#348).
 * <p>
 * Two halves, because either one alone passes against a real defect. The behaviour tests below call {@code onReceive} directly, which proves what the
 * receiver decides but says nothing about whether the broadcast ever reaches it — deleting an action from the manifest leaves them all green. The
 * registration tests ask the package manager which receivers resolve each action, which is the half that pins the filter, and they in turn cannot see an
 * {@code onReceive} that ignores what it was handed. Both actions are asserted in both halves.
 * <p>
 * Update was the one that shipped broken: a reboot has always been covered, while replacing a package force-stops the app and a force-stop clears the
 * pending {@code START_STICKY} restart, so monitoring stayed down after every Play update until the user opened the app.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MonitoringRestartReceiverTest {

	private Application application;
	private MonitoringRestartReceiver receiver;

	@Before
	public void setUp() {
		application = ApplicationProvider.getApplicationContext();
		receiver = new MonitoringRestartReceiver();
		// Drop anything a previous test left queued, so getNextStartedService() can only return what this test caused.
		shadowOf(application).clearStartedServices();
	}

	/** Deliver an action straight to the receiver, bypassing the manifest filter the registration tests cover separately. */
	private void receive(String action) {
		receiver.onReceive(application, new Intent(action));
	}

	/** The component of the one service the receiver asked for, or null when it asked for none. */
	private ComponentName startedService() {
		final Intent started = shadowOf(application).getNextStartedService();
		return isNull(started) ? null : started.getComponent();
	}

	private ComponentName monitoringService() {
		return new ComponentName(application, PowerConnectionService.class);
	}

	/** The app's own registration for an action, asked of the package manager rather than read off the manifest file, or null when it has none. */
	private ResolveInfo handlerFor(String action) {
		final Intent broadcast = new Intent(action).setPackage(application.getPackageName());

		return application.getPackageManager().queryBroadcastReceivers(broadcast, 0).stream()
			.filter(candidate -> MonitoringRestartReceiver.class.getName().equals(candidate.activityInfo.name))
			.findFirst()
			.orElse(null);
	}

	@Test
	public void bootStartsMonitoring() {
		receive(Intent.ACTION_BOOT_COMPLETED);

		assertEquals("boot did not start monitoring", monitoringService(), startedService());
	}

	@Test
	public void anAppUpdateStartsMonitoring() {
		receive(Intent.ACTION_MY_PACKAGE_REPLACED);

		assertEquals("an app update did not start monitoring", monitoringService(), startedService());
	}

	/**
	 * The receiver re-checks the action rather than trusting that only its filter can reach it, so an action it never registered for starts nothing.
	 * Drop that check and this is the only test that notices.
	 */
	@Test
	public void anotherAppBeingReplacedStartsNothing() {
		receive(Intent.ACTION_PACKAGE_REPLACED);

		assertNull("a broadcast the receiver is not registered for started a service", startedService());
	}

	/** A receiver is handed a null intent on an unordered broadcast with no action in some framework paths; it must not take that as a reason to start. */
	@Test
	public void aNullIntentStartsNothing() {
		receiver.onReceive(application, null);

		assertNull("a null intent started a service", startedService());
	}

	@Test
	public void theManifestRegistersBoot() {
		assertTrue("nothing in the app is registered for BOOT_COMPLETED", nonNull(handlerFor(Intent.ACTION_BOOT_COMPLETED)));
	}

	@Test
	public void theManifestRegistersTheAppBeingReplaced() {
		assertTrue("nothing in the app is registered for MY_PACKAGE_REPLACED", nonNull(handlerFor(Intent.ACTION_MY_PACKAGE_REPLACED)));
	}

	/**
	 * Boot broadcasts only reach an exported receiver on Android 12+, and {@code exported} is the kind of attribute a later edit drops while tidying, since
	 * nothing about the app looks different when it goes.
	 */
	@Test
	public void theReceiverIsExportedSoTheSystemCanReachIt() {
		final ResolveInfo handler = handlerFor(Intent.ACTION_BOOT_COMPLETED);

		assertNotNull("the receiver is not registered for boot at all", handler);
		assertTrue("the receiver is not exported, so the system cannot deliver boot to it", handler.activityInfo.exported);
	}
}
