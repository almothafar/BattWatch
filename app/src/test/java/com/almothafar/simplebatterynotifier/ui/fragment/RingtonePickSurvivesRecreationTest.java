package com.almothafar.simplebatterynotifier.ui.fragment;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.ui.SettingsActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * The sound picker's result has to survive the settings screen being destroyed while the system ringtone picker is in front of it (#305).
 *
 * <p>The picker is a separate activity, so anything that recreates this fragment while it is showing — a rotation, a font-size change, the process being
 * reclaimed — used to lose the pick outright: the waiting preference lived in a plain field, came back null, and the result was dropped with no feedback.
 * That is a narrower and worse route to the same complaint as #303, where the pick was at least saved.
 *
 * <p>The recreation here is a real one, driven through {@link ActivityController#recreate()} rather than simulated by calling lifecycle methods, so the
 * fragment is genuinely rebuilt from its saved state and the preference hierarchy genuinely re-inflated. That matters: the object identity the old code
 * depended on cannot survive it even in principle, which is why the fix saves a key instead of a reference.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RingtonePickSurvivesRecreationTest {

	/** A pick that is nothing like a device default, so "the new sound was applied" cannot pass by coincidence. */
	private static final String PICKED = "content://media/internal/audio/media/63";
	private static final String PREVIOUS = "content://settings/system/notification_sound";

	/** Restated rather than read from {@code NotificationChannels}, which is package-private to the service package. */
	private static final String CRITICAL_BASE_ID = "battery_critical";

	private Context context;
	private NotificationManager manager;
	private ActivityController<SettingsActivity> controller;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		manager = context.getSystemService(NotificationManager.class);

		// Build the channels the pick has to replace, so what follows is a sound changing rather than one appearing.
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.edit().putString(context.getString(R.string._pref_key_notifications_alert_sound_ringtone), PREVIOUS).commit();
		NotificationService.refreshAlertChannelsIfAffected(context, context.getString(R.string._pref_key_notifications_alert_sound_ringtone));

		controller = Robolectric.buildActivity(SettingsActivity.class).setup();
		showAlertsScreen();
	}

	@After
	public void tearDown() {
		controller.close();
	}

	@Test
	public void aPickMadeAfterTheScreenIsRecreated_stillReachesTheLiveChannel() {
		// The critical picker, deliberately: it is the one sound preference with no android:dependency, so it is enabled without arranging a switch first
		// and performClick actually reaches the listener.
		criticalSoundPreference().performClick();

		final Fragment before = alertsFragment();
		controller.recreate();
		final GenericPreferenceFragment after = alertsFragment();
		assertNotSame("the fragment was not actually recreated, so this test proves nothing", before, after);

		final String idBefore = currentCriticalChannel().getId();
		after.applyPendingRingtonePick(PICKED);

		final NotificationChannel live = currentCriticalChannel();
		assertNotNull("the critical channel went missing across the re-version", live);
		assertNotEquals("a channel kept under its old ID is one Android un-deleted with its old sound", idBefore, live.getId());
		assertEquals("the pick is only audible once the channel itself carries it", Uri.parse(PICKED), live.getSound());
	}

	/**
	 * The pending picker is remembered by key, so it resolves against the rebuilt hierarchy rather than pointing at the discarded object that was clicked.
	 */
	@Test
	public void theRecreatedScreenResolvesThePickerAgainRatherThanReusingTheClickedObject() {
		final Preference clicked = criticalSoundPreference();
		clicked.performClick();

		controller.recreate();

		assertNotSame("the hierarchy was not re-inflated, so resolving by key is untested here", clicked, criticalSoundPreference());
		alertsFragment().applyPendingRingtonePick(PICKED);

		assertEquals(Uri.parse(PICKED), currentCriticalChannel().getSound());
	}

	/**
	 * A result arriving with nothing pending must not be guessed at. Applying it to some default severity would silently retune an alert the user never
	 * touched, which is worse than the loss this fix exists to prevent.
	 */
	@Test
	public void aResultWithNothingPending_changesNothing() {
		final String idBefore = currentCriticalChannel().getId();

		alertsFragment().applyPendingRingtonePick(PICKED);

		assertEquals("no picker was waiting, so nothing should have been rebuilt", idBefore, currentCriticalChannel().getId());
		assertEquals(Uri.parse(PREVIOUS), currentCriticalChannel().getSound());
	}

	/**
	 * Put the Alerts preference screen on the activity, which is the screen the sound pickers live on.
	 */
	private void showAlertsScreen() {
		final GenericPreferenceFragment fragment = new GenericPreferenceFragment();
		final Bundle args = new Bundle();
		args.putString("category", context.getString(R.string.pref_category_alerts));
		fragment.setArguments(args);
		controller.get()
		          .getSupportFragmentManager()
		          .beginTransaction()
		          .replace(R.id.settings_container, fragment)
		          .commitNow();
	}

	/**
	 * @return the Alerts fragment the activity currently holds
	 */
	private GenericPreferenceFragment alertsFragment() {
		final Fragment fragment = controller.get().getSupportFragmentManager().findFragmentById(R.id.settings_container);
		assertTrue("the Alerts screen is not showing", fragment instanceof GenericPreferenceFragment);
		return (GenericPreferenceFragment) fragment;
	}

	/**
	 * @return the critical alert's sound picker on the screen as it currently stands
	 */
	private Preference criticalSoundPreference() {
		final Preference preference = alertsFragment().findPreference(context.getString(R.string._pref_key_notifications_alert_sound_ringtone));
		assertNotNull("the critical sound picker is missing from the Alerts screen", preference);
		return preference;
	}

	/**
	 * The critical alert's channel as the system currently holds it, found by ID prefix rather than by rebuilding the version suffix, so the test does not
	 * restate the naming rule it exists to check.
	 *
	 * @return the live critical alert channel, or null when none is registered
	 */
	private NotificationChannel currentCriticalChannel() {
		for (NotificationChannel channel : manager.getNotificationChannels()) {
			if (channel.getId().equals(CRITICAL_BASE_ID) || channel.getId().startsWith(CRITICAL_BASE_ID + "_v")) {
				return channel;
			}
		}
		return null;
	}
}
