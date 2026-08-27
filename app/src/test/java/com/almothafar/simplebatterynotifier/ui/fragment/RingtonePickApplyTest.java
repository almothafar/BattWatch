package com.almothafar.simplebatterynotifier.ui.fragment;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.ui.preference.RingtonePreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GenericPreferenceFragment#applyRingtonePick} — that choosing a sound reaches the live notification channel, which is the only place a sound
 * becomes audible from (#303).
 *
 * <p>Saving the pick was never the broken part: the URI reached preferences correctly and the channel registry read it correctly. What was missing is the
 * step between them. The picker result arrives once the fragment reaches STARTED, while the preference-change listener that reports every other channel
 * setting is only registered in {@code onResume}, so the write announced itself to nobody and the channels were never re-versioned. Android freezes a
 * channel's sound at creation, so the pick stayed inaudible for good rather than applying on the next alert.
 *
 * <p>The assertions are therefore made against the channel the system would actually play, not against the call that rebuilds it: a test that only
 * verified {@code refreshAlertChannelsIfAffected} was invoked would have passed against the shipped bug had the wiring merely been in a different place,
 * and says nothing about the sound a user hears. Both halves are checked, because either alone can be right while the pick stays silent — a channel
 * carrying the new URI under the <em>old</em> ID is one Android un-deleted with its old settings, and a fresh ID carrying the old sound is a rebuild that
 * ran before the value was stored.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RingtonePickApplyTest {

	/** A pick that is nothing like a device default, so "the new sound was applied" cannot pass by coincidence. */
	private static final String PICKED = "content://media/internal/audio/media/63";
	private static final String PREVIOUS = "content://settings/system/notification_sound";

	/** Restated rather than read from {@code NotificationChannels}, which is package-private to the service package. */
	private static final String CRITICAL_BASE_ID = "battery_critical";

	private Context context;
	private NotificationManager manager;
	private SharedPreferences prefs;
	private String soundKey;
	private RingtonePreference picker;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		manager = context.getSystemService(NotificationManager.class);
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		soundKey = context.getString(R.string._pref_key_notifications_alert_sound_ringtone);

		// Build the channels the pick has to replace, so what follows is a sound changing rather than one appearing.
		prefs.edit().putString(soundKey, PREVIOUS).commit();
		NotificationService.refreshAlertChannelsIfAffected(context, soundKey);

		picker = mock(RingtonePreference.class);
		when(picker.getKey()).thenReturn(soundKey);
		// The real preference persists through persistString; the stub does the same, so the channels are rebuilt from a stored value and a rebuild
		// ordered before the write shows up as the old sound instead of passing silently.
		doAnswer(invocation -> {
			prefs.edit().putString(soundKey, invocation.getArgument(0, String.class)).commit();
			return null;
		}).when(picker).setRingtoneUri(anyString());
	}

	@Test
	public void aNewPick_reachesTheLiveChannel() {
		final String idBefore = currentCriticalChannel().getId();

		GenericPreferenceFragment.applyRingtonePick(context, picker, PICKED);

		final NotificationChannel live = currentCriticalChannel();
		assertNotNull("the critical channel went missing across the re-version", live);
		assertNotEquals("a channel kept under its old ID is one Android un-deleted with its old sound", idBefore, live.getId());
		assertEquals("the pick is only audible once the channel itself carries it", Uri.parse(PICKED), live.getSound());
	}

	/**
	 * "Silent" is a choice like any other, and the one most easily mistaken for "nothing was picked" — the picker persists it as an empty URI, so a guard
	 * written as a null/empty check would drop it and leave the previous sound playing.
	 */
	@Test
	public void pickingSilent_reachesTheLiveChannelToo() {
		final String idBefore = currentCriticalChannel().getId();

		GenericPreferenceFragment.applyRingtonePick(context, picker, "");

		final NotificationChannel live = currentCriticalChannel();
		assertNotNull("the critical channel went missing across the re-version", live);
		assertNotEquals("a channel kept under its old ID is one Android un-deleted with its old sound", idBefore, live.getId());
		assertNull("Silent is a choice, and the channel expresses it as no sound at all", live.getSound());
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
