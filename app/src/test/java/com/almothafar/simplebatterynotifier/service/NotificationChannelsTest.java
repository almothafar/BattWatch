package com.almothafar.simplebatterynotifier.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link NotificationChannels} — the channel registry's two settings-driven behaviours: the versioned IDs that let a
 * changed setting apply at all (issue #153), and the per-severity sound each alert channel is created with (#286).
 */
@RunWith(Enclosed.class)
public class NotificationChannelsTest {

	/** A picked sound that is nothing like the device default, so "the pick applied" can't pass by coincidence. */
	private static final String PICKED_CRITICAL = "content://media/internal/audio/media/101";
	private static final String PICKED_WARNING = "content://media/internal/audio/media/202";
	private static final String PICKED_FULL = "content://media/internal/audio/media/303";

	/**
	 * The six alert channels, restated rather than read from the production table on purpose: a test that enumerated the
	 * same list the code iterates could not notice a channel dropping out of it.
	 */
	private static final String[] BASE_CHANNEL_IDS = {
			NotificationChannels.CHANNEL_ID_CRITICAL,
			NotificationChannels.CHANNEL_ID_WARNING,
			NotificationChannels.CHANNEL_ID_FULL,
			NotificationChannels.CHANNEL_ID_TEMPERATURE,
			NotificationChannels.CHANNEL_ID_FAST_DRAIN,
			NotificationChannels.CHANNEL_ID_SLOW_CHARGE,
	};

	/**
	 * {@link NotificationChannels#versionedChannelId(String, int)} — versioned alert-channel IDs so a changed setting
	 * creates genuinely new channels instead of un-deleting old ones (issue #153). Version 1 must stay the original
	 * unsuffixed ID so existing installs keep their channels.
	 */
	@RunWith(Parameterized.class)
	public static class VersionedIds {

		@Parameter(0) public String label;
		@Parameter(1) public String baseId;
		@Parameter(2) public int version;
		@Parameter(3) public String expected;

		@Parameters(name = "{0}")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{
					{"version 1 keeps the legacy unsuffixed ID", "battery_critical", 1, "battery_critical"},
					{"version 2 appends _v2", "battery_critical", 2, "battery_critical_v2"},
					{"later versions keep counting", "battery_warning", 7, "battery_warning_v7"},
					{"different base IDs stay distinct", "battery_full", 2, "battery_full_v2"},
					{"defensive: version 0 treated as legacy", "battery_critical", 0, "battery_critical"},
					{"defensive: negative version treated as legacy", "battery_critical", -3, "battery_critical"},
			});
		}

		@Test
		public void matchesExpected() {
			assertEquals(label, expected, NotificationChannels.versionedChannelId(baseId, version));
		}
	}

	/**
	 * The sound each alert channel is created with (#286).
	 * <p>
	 * On Android 8+ the channel owns the sound, and {@code createOrUpdateAlertChannel} never set one — so the three
	 * pickers on Settings › Alerts saved a choice and every alert went on playing the framework default. These pin
	 * that the pick reaches the channel, and which of the three picks each of the six channels takes.
	 */
	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34)
	public static class ChannelSounds {

		private Context context;
		private NotificationManager manager;

		@Before
		public void setUp() {
			context = ApplicationProvider.getApplicationContext();
			manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			pickSounds(context, PICKED_CRITICAL, PICKED_WARNING, PICKED_FULL);
		}

		@Test
		public void criticalChannel_playsTheCriticalPick() {
			NotificationChannels.ensureChannels(context);

			assertEquals(Uri.parse(PICKED_CRITICAL), soundOf(NotificationChannels.CHANNEL_ID_CRITICAL));
		}

		@Test
		public void warningChannel_playsTheWarningPick() {
			NotificationChannels.ensureChannels(context);

			assertEquals(Uri.parse(PICKED_WARNING), soundOf(NotificationChannels.CHANNEL_ID_WARNING));
		}

		@Test
		public void fullChannel_playsTheFullPick() {
			NotificationChannels.ensureChannels(context);

			assertEquals(Uri.parse(PICKED_FULL), soundOf(NotificationChannels.CHANNEL_ID_FULL));
		}

		@Test
		public void temperatureChannel_sharesTheCriticalPick() {
			NotificationChannels.ensureChannels(context);

			// Overheating is the critical bucket's second member (#223): "act now", same as a critically low battery.
			assertEquals(Uri.parse(PICKED_CRITICAL), soundOf(NotificationChannels.CHANNEL_ID_TEMPERATURE));
		}

		@Test
		public void fastDrainChannel_sharesTheWarningPick() {
			NotificationChannels.ensureChannels(context);

			// The two rate alerts belong to the warning bucket. They reached the critical pref by accident before, which
			// is the half of #286 that was a mapping bug rather than missing wiring.
			assertEquals(Uri.parse(PICKED_WARNING), soundOf(NotificationChannels.CHANNEL_ID_FAST_DRAIN));
		}

		@Test
		public void slowChargeChannel_sharesTheWarningPick() {
			NotificationChannels.ensureChannels(context);

			assertEquals(Uri.parse(PICKED_WARNING), soundOf(NotificationChannels.CHANNEL_ID_SLOW_CHARGE));
		}

		@Test
		public void silentPick_leavesTheChannelWithNoSound() {
			// The picker's "Silent" option persists an empty string, which is a choice and not an unset value: parsed
			// rather than translated it becomes an empty Uri that resolves to nothing at post time.
			pickSound(context, R.string._pref_key_notifications_full_sound_ringtone, "");

			NotificationChannels.ensureChannels(context);

			assertNull(soundOf(NotificationChannels.CHANNEL_ID_FULL));
		}

		@Test
		public void unpickedSound_fallsBackToTheDeviceDefault() {
			clearSounds(context);

			NotificationChannels.ensureChannels(context);

			final Uri deviceDefault = Uri.parse(context.getString(R.string._default_notification_sound_uri));
			assertEquals(deviceDefault, soundOf(NotificationChannels.CHANNEL_ID_CRITICAL));
		}

		@Test
		public void alertSound_playsUnderNotificationUsage() {
			NotificationChannels.ensureChannels(context);

			// USAGE_NOTIFICATION, not USAGE_ALARM: the channel's sound has to be the one the system silences in DND and
			// silent mode, because that is exactly when AlertSounds' override-silent path takes over. Both firing at once
			// is the double-up #286 has to avoid.
			final AudioAttributes attributes = channel(NotificationChannels.CHANNEL_ID_CRITICAL).getAudioAttributes();
			assertEquals(AudioAttributes.USAGE_NOTIFICATION, attributes.getUsage());
			assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, attributes.getContentType());
		}

		@Test
		public void quietHoursChannel_staysSilentWhateverIsPicked() {
			NotificationChannels.ensureChannels(context);

			// The quiet-hours channel is where an alert is rerouted outside the notification window (#111). A sound pick
			// must not follow it there.
			assertNull(manager.getNotificationChannel(NotificationChannels.CHANNEL_ID_ALERTS_SILENT).getSound());
		}

		private Uri soundOf(String baseChannelId) {
			return channel(baseChannelId).getSound();
		}

		private NotificationChannel channel(String baseChannelId) {
			return currentChannel(context, manager, baseChannelId);
		}
	}

	/**
	 * A changed setting has to re-version the channels to take effect, and an install on an older definition has to be
	 * re-versioned without changing any setting at all (#153/#286).
	 * <p>
	 * A channel's sound is fixed when it is created, and Android un-deletes a channel recreated under the same ID with
	 * its old settings — so without a version bump the new pick is stored, shown in the summary, and ignored. That is how
	 * the Vibrate toggle failed before #153, and how a sound pick would fail with the wiring alone.
	 * <p>
	 * The re-version is driven through {@link NotificationService#refreshAlertChannelsIfAffected}, which is the single
	 * call the settings screen makes: testing the predicate and the recreation separately leaves the join untested, and
	 * the join is the part both #153 and #286 got wrong.
	 */
	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34)
	public static class SettingChangesReVersion {

		private Context context;
		private NotificationManager manager;

		@Before
		public void setUp() {
			context = ApplicationProvider.getApplicationContext();
			manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			clearSounds(context);
			NotificationChannels.ensureChannels(context);
		}

		@Test
		public void changedPick_bumpsTheChannelVersion() {
			final int before = channelVersion(context);
			pickSound(context, R.string._pref_key_notifications_alert_sound_ringtone, PICKED_CRITICAL);

			assertTrue(NotificationService.refreshAlertChannelsIfAffected(context, key(R.string._pref_key_notifications_alert_sound_ringtone)));

			// Asserted as a move rather than against a literal, so raising the definition version doesn't rewrite this test.
			assertTrue("a changed pick that leaves the version alone is a pick the system will ignore", channelVersion(context) > before);
		}

		@Test
		public void changedPick_recreatesEveryChannelUnderTheNewIds() {
			pickSound(context, R.string._pref_key_notifications_warning_sound_ringtone, PICKED_WARNING);

			NotificationService.refreshAlertChannelsIfAffected(context, key(R.string._pref_key_notifications_warning_sound_ringtone));

			for (String baseId : BASE_CHANNEL_IDS) {
				assertNotNull(baseId + " went missing across the re-version", currentChannel(context, manager, baseId));
			}
			assertEquals(Uri.parse(PICKED_WARNING), currentChannel(context, manager, NotificationChannels.CHANNEL_ID_FAST_DRAIN).getSound());
		}

		@Test
		public void changedPick_leavesNoOrphanBehindInSystemSettings() {
			final String[] oldIds = currentChannelIds(context);
			pickSound(context, R.string._pref_key_notifications_alert_sound_ringtone, PICKED_CRITICAL);

			NotificationService.refreshAlertChannelsIfAffected(context, key(R.string._pref_key_notifications_alert_sound_ringtone));

			// Every one of the six, not just the first: an orphan is a per-channel failure, so a channel missed out of the
			// deletion list leaves a stale entry the user sees in system settings and a single-channel assertion passes.
			for (String oldId : oldIds) {
				assertNull(oldId + " was left behind in system settings", manager.getNotificationChannel(oldId));
			}
		}

		@Test
		public void theVibrateToggleStillReVersions() {
			final int before = channelVersion(context);

			assertTrue(NotificationService.refreshAlertChannelsIfAffected(context, key(R.string._pref_key_notifications_vibrate)));
			assertTrue(channelVersion(context) > before);
		}

		@Test
		public void everySoundPickReVersions() {
			assertReVersions(R.string._pref_key_notifications_alert_sound_ringtone);
			assertReVersions(R.string._pref_key_notifications_warning_sound_ringtone);
			assertReVersions(R.string._pref_key_notifications_full_sound_ringtone);
		}

		@Test
		public void anUnrelatedPreferenceDoesNot() {
			// Every preference change on the screen comes through here, so re-versioning for one that changes nothing about
			// the channels would reset the user's per-channel tweaks for nothing.
			final int before = channelVersion(context);

			assertFalse(NotificationService.refreshAlertChannelsIfAffected(context, key(R.string._pref_key_charge_target)));
			assertFalse(NotificationService.refreshAlertChannelsIfAffected(context, null));
			assertEquals(before, channelVersion(context));
		}

		private void assertReVersions(int keyRes) {
			final int before = channelVersion(context);
			assertTrue(key(keyRes) + " must re-version the channels", NotificationService.refreshAlertChannelsIfAffected(context, key(keyRes)));
			assertTrue(key(keyRes) + " must re-version the channels", channelVersion(context) > before);
		}

		private String key(int keyRes) {
			return context.getString(keyRes);
		}
	}

	/**
	 * An install that predates #286 has to start playing its pick without touching a setting.
	 * <p>
	 * This is the population #286 was filed about: the pick was saved long ago, the channels were created by code that
	 * never called {@code setSound}, and the user has no reason to go back and pick again. Passing the URI on every alert
	 * changes nothing there — Android froze the channel's sound when it was created — so unless the app re-versions the
	 * channels on its own, the fix reaches everyone except the people who reported it.
	 */
	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34)
	public static class DefinitionVersionMigration {

		private Context context;
		private NotificationManager manager;

		@Before
		public void setUp() {
			context = ApplicationProvider.getApplicationContext();
			manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			pickSound(context, R.string._pref_key_notifications_alert_sound_ringtone, PICKED_CRITICAL);
		}

		@Test
		public void theOldPick_becomesAudibleWithoutTheUserRePicking() {
			seedPre286Install(1);

			NotificationChannels.ensureChannels(context);

			assertEquals(Uri.parse(PICKED_CRITICAL), currentChannel(context, manager, NotificationChannels.CHANNEL_ID_CRITICAL).getSound());
		}

		@Test
		public void theOldChannels_areNotLeftBehind() {
			seedPre286Install(1);

			NotificationChannels.ensureChannels(context);

			// versionedChannelId(base, 1) is the bare base ID, so this is the pre-#286 channel itself.
			assertNull(manager.getNotificationChannel(NotificationChannels.CHANNEL_ID_CRITICAL));
		}

		@Test
		public void anInstallThatHadAlreadyChangedASetting_isMigratedToo() {
			// The settings version counts the user's own changes, so a long-standing install sits at 2 or higher while its
			// channels still predate #286. Keying the migration on that version instead of on the definition generation
			// would skip exactly these installs — the oldest ones, and the likeliest to have picked a sound years ago.
			seedPre286Install(4);

			NotificationChannels.ensureChannels(context);

			assertEquals(Uri.parse(PICKED_CRITICAL), currentChannel(context, manager, NotificationChannels.CHANNEL_ID_CRITICAL).getSound());
			assertNull(manager.getNotificationChannel(NotificationChannels.versionedChannelId(NotificationChannels.CHANNEL_ID_CRITICAL, 4)));
			assertTrue(channelVersion(context) > 4);
		}

		@Test
		public void theMigration_runsOnceAndThenLeavesTheVersionAlone() {
			seedPre286Install(1);

			NotificationChannels.ensureChannels(context);
			final int afterMigration = channelVersion(context);
			NotificationChannels.ensureChannels(context);
			NotificationChannels.ensureChannels(context);

			// ensureChannels runs on every alert. A migration that re-fired would throw away the user's per-channel tweaks
			// over and over, which is worse than the bug it exists to fix.
			assertTrue(afterMigration > 1);
			assertEquals(afterMigration, channelVersion(context));
		}

		/**
		 * Put the install in the state this migration exists for: channels created before #286 (so carrying no sound of
		 * their own) at a given settings version, and no record of which definition generation made them.
		 *
		 * @param version the settings version the install's channels live at
		 */
		private void seedPre286Install(int version) {
			setChannelVersion(context, version);
			manager.createNotificationChannel(new NotificationChannel(
					NotificationChannels.versionedChannelId(NotificationChannels.CHANNEL_ID_CRITICAL, version),
					"Critical", NotificationManager.IMPORTANCE_HIGH));
		}
	}

	/**
	 * The channel whose ID a base ID currently resolves to, at whatever settings version the install is on.
	 *
	 * @param context the test's application context
	 * @param manager the notification manager to read from
	 * @param baseId  the unversioned channel ID
	 *
	 * @return the live channel for that base ID
	 */
	private static NotificationChannel currentChannel(Context context, NotificationManager manager, String baseId) {
		return manager.getNotificationChannel(NotificationChannels.channelFor(context, true, baseId));
	}

	/**
	 * The versioned IDs the six alert channels currently live under.
	 *
	 * @param context the test's application context
	 *
	 * @return the current channel IDs, in the order of {@link #BASE_CHANNEL_IDS}
	 */
	private static String[] currentChannelIds(Context context) {
		final String[] ids = new String[BASE_CHANNEL_IDS.length];
		for (int i = 0; i < BASE_CHANNEL_IDS.length; i++) {
			ids[i] = NotificationChannels.channelFor(context, true, BASE_CHANNEL_IDS[i]);
		}
		return ids;
	}

	/**
	 * The stored alert-channel settings version. Read by its literal key, which is what the persisted state actually is.
	 *
	 * @param context the test's application context
	 *
	 * @return the current version
	 */
	private static int channelVersion(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getInt("alert_channel_version", 1);
	}

	/**
	 * Put the install on a given alert-channel settings version.
	 *
	 * @param context the test's application context
	 * @param version the version to store
	 */
	private static void setChannelVersion(Context context, int version) {
		PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("alert_channel_version", version).commit();
	}

	/**
	 * Store all three sound picks.
	 *
	 * @param context  the test's application context
	 * @param critical the critical bucket's pick
	 * @param warning  the warning bucket's pick
	 * @param full     the full-charge bucket's pick
	 */
	private static void pickSounds(Context context, String critical, String warning, String full) {
		pickSound(context, R.string._pref_key_notifications_alert_sound_ringtone, critical);
		pickSound(context, R.string._pref_key_notifications_warning_sound_ringtone, warning);
		pickSound(context, R.string._pref_key_notifications_full_sound_ringtone, full);
	}

	/**
	 * Store one sound pick, the way the ringtone picker does.
	 *
	 * @param context  the test's application context
	 * @param keyRes   the pick's preference key
	 * @param soundUri the URI to store; empty for the picker's "Silent" option
	 */
	private static void pickSound(Context context, int keyRes, String soundUri) {
		PreferenceManager.getDefaultSharedPreferences(context).edit().putString(context.getString(keyRes), soundUri).commit();
	}

	/**
	 * Leave every sound unpicked, as on a fresh install.
	 *
	 * @param context the test's application context
	 */
	private static void clearSounds(Context context) {
		PreferenceManager.getDefaultSharedPreferences(context)
		                 .edit()
		                 .remove(context.getString(R.string._pref_key_notifications_alert_sound_ringtone))
		                 .remove(context.getString(R.string._pref_key_notifications_warning_sound_ringtone))
		                 .remove(context.getString(R.string._pref_key_notifications_full_sound_ringtone))
		                 .commit();
	}
}
