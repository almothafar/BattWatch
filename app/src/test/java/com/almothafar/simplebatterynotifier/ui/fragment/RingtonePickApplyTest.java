package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.ui.preference.RingtonePreference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GenericPreferenceFragment#applyRingtonePick} — that choosing a sound re-creates the alert channels, which is what makes the choice audible
 * (#303).
 * <p>
 * Persisting the pick was never the broken part: the URI reached preferences correctly and the channel registry read it correctly. What was missing is
 * the step between them. The picker result arrives once the fragment reaches STARTED, while the preference-change listener that reports every other
 * channel setting is only registered in {@code onResume}, so the write announced itself to nobody and the channels were never re-versioned. Android
 * freezes a channel's sound at creation and only a change re-versions it, so the pick stayed inaudible for good rather than applying on the next alert.
 * <p>
 * The refresh call is therefore the behaviour under test, not an implementation detail — it is the only thing standing between a saved pick and a silent
 * one. {@link NotificationService} is mocked statically because the assertion is that this path <em>reports</em> the change; which preferences count as
 * channel settings is the registry's decision and is covered by {@code NotificationChannelsTest}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RingtonePickApplyTest {

	/** A pick that is nothing like a device default, so "the new sound was applied" cannot pass by coincidence. */
	private static final String PICKED = "content://media/internal/audio/media/63";
	private static final String PREVIOUS = "content://settings/system/notification_sound";
	private static final String KEY = "key_notifications_alert_sound_ringtone";

	@Test
	public void aNewPick_persistsAndRefreshesTheAlertChannels() {
		final Context context = ApplicationProvider.getApplicationContext();
		final RingtonePreference preference = mock(RingtonePreference.class);
		when(preference.getRingtoneUri()).thenReturn(PREVIOUS);
		when(preference.getKey()).thenReturn(KEY);

		try (MockedStatic<NotificationService> notifications = mockStatic(NotificationService.class)) {
			GenericPreferenceFragment.applyRingtonePick(context, preference, PICKED);

			verify(preference).setRingtoneUri(PICKED);
			notifications.verify(() -> NotificationService.refreshAlertChannelsIfAffected(any(Context.class), eq(KEY)));
		}
	}

	/**
	 * "Silent" is a choice like any other, and the one most likely to be mistaken for "nothing was picked" — an empty URI is what the picker persists for
	 * it, so a guard written as a null/empty check would drop it.
	 */
	@Test
	public void pickingSilent_refreshesTheAlertChannelsToo() {
		final Context context = ApplicationProvider.getApplicationContext();
		final RingtonePreference preference = mock(RingtonePreference.class);
		when(preference.getRingtoneUri()).thenReturn(PREVIOUS);
		when(preference.getKey()).thenReturn(KEY);

		try (MockedStatic<NotificationService> notifications = mockStatic(NotificationService.class)) {
			GenericPreferenceFragment.applyRingtonePick(context, preference, "");

			verify(preference).setRingtoneUri("");
			notifications.verify(() -> NotificationService.refreshAlertChannelsIfAffected(any(Context.class), eq(KEY)));
		}
	}

	/**
	 * Re-picking the sound already in force must not re-version anything: doing so deletes and recreates the channels, discarding whatever the user set on
	 * them in system settings.
	 */
	@Test
	public void rePickingTheSameSound_leavesTheChannelsAlone() {
		final Context context = ApplicationProvider.getApplicationContext();
		final RingtonePreference preference = mock(RingtonePreference.class);
		when(preference.getRingtoneUri()).thenReturn(PICKED);
		when(preference.getKey()).thenReturn(KEY);

		try (MockedStatic<NotificationService> notifications = mockStatic(NotificationService.class)) {
			GenericPreferenceFragment.applyRingtonePick(context, preference, PICKED);

			notifications.verify(() -> NotificationService.refreshAlertChannelsIfAffected(any(Context.class), any(String.class)), never());
		}
	}
}
