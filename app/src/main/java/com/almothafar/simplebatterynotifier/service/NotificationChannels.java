package com.almothafar.simplebatterynotifier.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;

import androidx.preference.PreferenceManager;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.util.AppPrefs;

import static java.util.Objects.isNull;

/**
 * The notification-channel registry (issue #166): creates, updates, refreshes and resolves the app's
 * notification channels. Split out of {@code NotificationService} so channel bookkeeping lives in one
 * place.
 * <p>
 * The six alert channels are listed once, in {@link #alertChannels()}. They used to be spelled out three times over —
 * created, deleted on refresh, and mapped to a sound — so adding one meant remembering all three, and forgetting the
 * deletion left an orphan in the user's system settings.
 * <p>
 * Each entry's ID is a <em>base</em> ID: the actual channel ID carries a version suffix (see {@link #versionedChannelId})
 * because Android un-deletes a channel recreated under the same ID, restoring its old settings — which made the Vibrate
 * toggle a no-op (issue #153). {@link #refreshAlertChannels} bumps the version so a changed setting really applies, and
 * {@link #ALERT_CHANNEL_DEFINITION} does the same for installs that never change a setting at all.
 * <p>
 * From Android 8 the <em>channel</em> owns the alert sound, so the user's per-severity sound picks are applied here as
 * well. Until they were, every alert channel was created with the framework default and the three pickers on
 * Settings › Alerts saved a choice that changed nothing audible (issue #286). A sound pick is therefore as much a
 * channel setting as the Vibrate toggle — see {@link #affectsAlertChannels}.
 */
final class NotificationChannels {

	// Alert (audible, high-importance) channels — base IDs, see class javadoc and alertChannels().
	static final String CHANNEL_ID_CRITICAL = "battery_critical";
	static final String CHANNEL_ID_WARNING = "battery_warning";
	static final String CHANNEL_ID_FULL = "battery_full";
	static final String CHANNEL_ID_TEMPERATURE = "battery_temperature";
	static final String CHANNEL_ID_FAST_DRAIN = "battery_fast_drain";
	static final String CHANNEL_ID_SLOW_CHARGE = "battery_slow_charge";
	// Silent, low-importance channels: the persistent status notification, and the quiet-hours channel
	// used to deliver an alert quietly during the user's quiet hours — still visible, no sound/vibration
	// (issue #111).
	static final String CHANNEL_ID_STATUS = "battery_status";
	static final String CHANNEL_ID_ALERTS_SILENT = "battery_alerts_quiet";

	// Current version of the alert channels' settings, stored in the default SharedPreferences.
	// Version 1 means the original unsuffixed channel IDs.
	private static final String PREF_ALERT_CHANNEL_VERSION = "alert_channel_version";
	// Which generation of the app's channel definitions this install's channels were created from. Absent (0) means they
	// predate issue #286. Deliberately separate from the version above, which counts the user's own setting changes and
	// so already reads 2 or more on any install that ever toggled Vibrate.
	private static final String PREF_ALERT_CHANNEL_DEFINITION = "alert_channel_definition";

	/**
	 * The generation of channel definitions this build creates. Raising it re-versions every install's alert channels
	 * once, on the next {@link #ensureChannels} — the only way a change to what the channels are <em>made of</em> reaches
	 * users who never change a setting themselves.
	 * <p>
	 * Generation 1 is issue #286. Everything before it created channels without ever calling {@code setSound}, so an
	 * install that had already picked a sound would have gone on playing the device default forever: the pick is passed
	 * on every alert and the system ignores it, and nothing else would ever re-version the channels. That is the exact
	 * population #286 was filed about, so the fix is inert without this.
	 * <p>
	 * Generation 2 is issue #303, and exists for the same reason one step along: generation 1 shipped, but the refresh it
	 * relies on never fired for a sound pick, so those installs recorded the pick in preferences and built their channels
	 * from the default anyway. Their stored value is already correct, and a re-version is only ever triggered by a
	 * <em>change</em>, so without a second generation the fix would reach only users who happen to pick a new sound.
	 */
	private static final int ALERT_CHANNEL_DEFINITION = 2;

	private NotificationChannels() {
		// Utility class - prevent instantiation
	}

	/**
	 * Create the notification channels if they don't exist, or refresh their name/description if they
	 * do (so translated names reach upgraded installs — issue #165).
	 *
	 * @param context The application context
	 */
	static void ensureChannels(Context context) {
		final NotificationManager manager = getManager(context);
		if (isNull(manager)) {
			return;
		}

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		migrateAlertChannels(manager, prefs);

		final int version = alertChannelVersion(prefs);
		final boolean vibrate = AppPrefs.vibrateEnabled(context);
		for (AlertChannel definition : alertChannels()) {
			manager.createNotificationChannel(definition.toNotificationChannel(context, prefs, version, vibrate));
		}

		createOrUpdateSilentChannel(manager, CHANNEL_ID_STATUS,
				context.getString(R.string.notification_status_channel_name),
				context.getString(R.string.notification_status_channel_description));
		createOrUpdateSilentChannel(manager, CHANNEL_ID_ALERTS_SILENT,
				context.getString(R.string.notification_quiet_channel_name),
				context.getString(R.string.notification_quiet_channel_description));
	}

	/**
	 * Re-create the alert channels so a changed "Vibrate" or sound preference takes effect.
	 * <p>
	 * Deleting and recreating a channel under the same ID is not enough: Android un-deletes it with
	 * its old settings (issue #153). So the old-version channels are deleted (keeping system
	 * settings free of orphans) and the channels are recreated under the next version's IDs, which
	 * the system treats as brand-new channels with the new settings. The silent channels are
	 * untouched.
	 *
	 * @param context The application context
	 */
	static void refreshAlertChannels(Context context) {
		final NotificationManager manager = getManager(context);
		if (isNull(manager)) {
			return;
		}

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		reVersionAlertChannels(manager, prefs);
		ensureChannels(context);
	}

	/**
	 * Whether a changed preference is one the alert channels are built from, so the channels have to be re-created
	 * for it to take effect: the "Vibrate" toggle (issue #153) and the per-severity sound picks (issue #286).
	 * <p>
	 * Android freezes a channel's sound and vibration at creation, so a setting that never reaches
	 * {@link #refreshAlertChannels} is simply ignored — which is what made both of these look like they worked and
	 * not. The sound keys come from {@link #alertChannels()} rather than being listed again here: a picker added
	 * without a matching entry in the membership test would save a choice that never re-versions anything, which is
	 * #286 all over again.
	 *
	 * @param context The application context
	 * @param prefKey the preference key that changed, or null
	 *
	 * @return true when the alert channels have to be re-created under a new version
	 */
	static boolean affectsAlertChannels(Context context, String prefKey) {
		if (isNull(prefKey)) {
			return false;
		}
		if (prefKey.equals(context.getString(R.string._pref_key_notifications_vibrate))) {
			return true;
		}
		for (AlertChannel definition : alertChannels()) {
			if (prefKey.equals(context.getString(definition.soundKeyRes()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The sound an alert channel plays: the URI the user picked for the severity bucket that channel belongs to.
	 * <p>
	 * Three pickers cover six channels, so the two rate alerts and the overheat alert — which have no picker of their
	 * own — take a bucket that is chosen rather than the critical pref they reached by accident (issue #286). The
	 * buckets are the ones #223 designs its bundled sounds around and are recorded per channel in
	 * {@link #alertChannels()}: the critical level and overheating are the same "act now" severity, while low battery,
	 * fast drain and slow charge are all "something is off, look at it".
	 * <p>
	 * The same resolution feeds the channel and {@link AlertSounds#playAlarm}, so the sound a user hears can't depend on
	 * which of the two paths delivered the alert. An empty result is a real answer, not a missing one: it is what the
	 * picker's "Silent" option persists, and {@link AlertSounds#soundUriOrSilent} is how both paths read it.
	 *
	 * @param context       The application context
	 * @param prefs         the default SharedPreferences, already held by every caller
	 * @param baseChannelId the unversioned channel ID whose sound is wanted
	 *
	 * @return the picked sound URI, empty when the user chose Silent, or the device's default notification sound
	 */
	static String alertSoundUri(Context context, SharedPreferences prefs, String baseChannelId) {
		return prefs.getString(context.getString(soundKeyFor(baseChannelId)), context.getString(R.string._default_notification_sound_uri));
	}

	/**
	 * The channel an alerting notification should post to: its normal audible (high-importance) channel
	 * when alerts may sound now, or the shared silent channel during quiet hours so the alert is still
	 * shown but makes no sound or vibration (issue #111). The audible channel's base ID is resolved to
	 * its current versioned ID (issue #153), so every posting site tracks version bumps automatically.
	 *
	 * @param context              The application context
	 * @param alertsAllowed        whether alerts may sound now (inside the window, or a critical override)
	 * @param audibleBaseChannelId the base channel ID to use when alerts are allowed to sound
	 * @return the channel id to post the notification on
	 */
	static String channelFor(Context context, boolean alertsAllowed, String audibleBaseChannelId) {
		if (!alertsAllowed) {
			return CHANNEL_ID_ALERTS_SILENT;
		}
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return versionedChannelId(audibleBaseChannelId, alertChannelVersion(prefs));
	}

	/**
	 * The alert-channel ID for a given settings version. Version 1 is the original unsuffixed ID;
	 * later versions append a suffix ("battery_critical_v2", ...) so Android sees a brand-new channel
	 * instead of un-deleting the old one with its stale settings (issue #153).
	 *
	 * @param baseChannelId the unversioned channel ID
	 * @param version       the alert-channel settings version (1-based)
	 * @return the channel ID to create and post on for that version
	 */
	static String versionedChannelId(String baseChannelId, int version) {
		return version <= 1 ? baseChannelId : baseChannelId + "_v" + version;
	}

	/**
	 * The six alert channels, each with everything about it that doesn't change between versions: its base ID, its
	 * translated name and description, its LED colour, and the sound preference whose severity bucket it belongs to.
	 * <p>
	 * Built per call rather than held in a static field so nothing in {@code android.graphics} is touched at class-init
	 * time, which would throw {@code Stub!} in the plain-JUnit tests that use this class's pure channel-ID helpers.
	 *
	 * @return the alert-channel definitions, in creation order
	 */
	private static AlertChannel[] alertChannels() {
		// The amber both rate alerts and the low-battery alert light up in.
		final int amber = Color.rgb(0xff, 0x66, 0x00);
		return new AlertChannel[]{
				new AlertChannel(CHANNEL_ID_CRITICAL,
						R.string.notification_critical_channel_name,
						R.string.notification_critical_channel_description,
						Color.RED,
						R.string._pref_key_notifications_alert_sound_ringtone),
				new AlertChannel(CHANNEL_ID_WARNING,
						R.string.notification_warning_channel_name,
						R.string.notification_warning_channel_description,
						amber,
						R.string._pref_key_notifications_warning_sound_ringtone),
				new AlertChannel(CHANNEL_ID_FULL,
						R.string.notification_full_channel_name,
						R.string.notification_full_channel_description,
						Color.GREEN,
						R.string._pref_key_notifications_full_sound_ringtone),
				new AlertChannel(CHANNEL_ID_TEMPERATURE,
						R.string.notification_temperature_channel_name,
						R.string.notification_temperature_channel_description,
						Color.RED,
						R.string._pref_key_notifications_alert_sound_ringtone),
				new AlertChannel(CHANNEL_ID_FAST_DRAIN,
						R.string.notification_fast_drain_channel_name,
						R.string.notification_fast_drain_channel_description,
						amber,
						R.string._pref_key_notifications_warning_sound_ringtone),
				new AlertChannel(CHANNEL_ID_SLOW_CHARGE,
						R.string.notification_slow_charge_channel_name,
						R.string.notification_slow_charge_channel_description,
						amber,
						R.string._pref_key_notifications_warning_sound_ringtone),
		};
	}

	/**
	 * The sound preference a base channel ID takes its pick from.
	 *
	 * @param baseChannelId the unversioned channel ID
	 *
	 * @return that channel's sound preference key resource
	 */
	private static int soundKeyFor(String baseChannelId) {
		for (AlertChannel definition : alertChannels()) {
			if (definition.baseId().equals(baseChannelId)) {
				return definition.soundKeyRes();
			}
		}
		// Deliberately the critical bucket rather than nothing, for a channel added without an entry above: an alert on a
		// neighbouring severity's sound is a far smaller failure than one that loses its sound.
		return R.string._pref_key_notifications_alert_sound_ringtone;
	}

	/**
	 * Re-version the alert channels once when this build's {@link #ALERT_CHANNEL_DEFINITION} is newer than the one the
	 * install's channels were created from.
	 * <p>
	 * Runs before the channels are created, so the same {@link #ensureChannels} call that migrates also creates the
	 * replacements. Keyed on the definition generation rather than on the settings version, because the version counts
	 * the user's own changes: an install that toggled Vibrate once is already on version 2, and a migration that tested
	 * "version below 2" would skip precisely the long-standing installs it exists for.
	 * <p>
	 * A fresh install has no channels to replace and still passes through here, so it starts one version up. That costs
	 * nothing — the version only names the channel IDs — and it beats testing "is this install new?", which the version
	 * preference cannot answer: an install that never changed a setting has never written it either.
	 *
	 * @param manager The NotificationManager
	 * @param prefs   the default SharedPreferences
	 */
	private static void migrateAlertChannels(NotificationManager manager, SharedPreferences prefs) {
		if (prefs.getInt(PREF_ALERT_CHANNEL_DEFINITION, 0) >= ALERT_CHANNEL_DEFINITION) {
			return;
		}
		reVersionAlertChannels(manager, prefs);
	}

	/**
	 * Delete the alert channels at the version the install is on and move it to the next one, so the channels
	 * {@link #ensureChannels} creates next are ones the system sees as brand new rather than un-deleting with their old
	 * settings (issue #153). Deleting first keeps the user's system settings free of orphans.
	 * <p>
	 * The single home of the re-version, shared by the user-driven {@link #refreshAlertChannels} and the one-time
	 * definition migration, so the two can't disagree about what re-versioning means. Recording the definition
	 * generation here is what stops the migration re-firing on every alert.
	 *
	 * @param manager The NotificationManager
	 * @param prefs   the default SharedPreferences
	 */
	private static void reVersionAlertChannels(NotificationManager manager, SharedPreferences prefs) {
		final int oldVersion = alertChannelVersion(prefs);
		for (AlertChannel definition : alertChannels()) {
			manager.deleteNotificationChannel(versionedChannelId(definition.baseId(), oldVersion));
		}
		prefs.edit()
		     .putInt(PREF_ALERT_CHANNEL_VERSION, oldVersion + 1)
		     .putInt(PREF_ALERT_CHANNEL_DEFINITION, ALERT_CHANNEL_DEFINITION)
		     .apply();
	}

	/**
	 * The current alert-channel settings version, bumped by {@link #refreshAlertChannels(Context)}
	 * whenever one of the {@link #affectsAlertChannels} preferences changes, and once more when this build's
	 * {@link #ALERT_CHANNEL_DEFINITION} is newer than the one the install's channels were created from.
	 *
	 * @param prefs the default SharedPreferences
	 * @return the current version, 1 for installs still on the original unsuffixed channels
	 */
	private static int alertChannelVersion(SharedPreferences prefs) {
		return prefs.getInt(PREF_ALERT_CHANNEL_VERSION, 1);
	}

	/**
	 * Create a low-importance, fully silent channel (no sound, vibration, lights or badge), or update
	 * its name/description if it already exists.
	 * <p>
	 * Used both by the persistent status notification and, during the user's quiet hours, to deliver
	 * an alert quietly so it stays visible without disturbing the user (issue #111).
	 * <p>
	 * As with the alert channels, re-calling for an existing ID updates only name/description, so
	 * translated names reach upgraded installs (#165) without disturbing the silent behaviour.
	 *
	 * @param manager     The NotificationManager
	 * @param channelId   The channel ID to create
	 * @param name        The channel name
	 * @param description The channel description
	 */
	private static void createOrUpdateSilentChannel(NotificationManager manager, String channelId, String name, String description) {
		final NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW);
		channel.setDescription(description);
		channel.enableLights(false);
		channel.enableVibration(false);
		channel.setSound(null, null);
		channel.setShowBadge(false);
		manager.createNotificationChannel(channel);
	}

	/**
	 * The attributes an alert channel's sound plays under: a notification, so it follows the notification stream's
	 * volume and the system's Do Not Disturb rules.
	 * <p>
	 * Deliberately not the {@code USAGE_ALARM} that {@link AlertSounds} plays under. That path only runs when the ringer
	 * is silenced or DND is on, and those are exactly the conditions in which a {@code USAGE_NOTIFICATION} sound is
	 * suppressed — so in normal ringer mode, silent mode and DND alike, one of the two plays and never both (issue #286,
	 * requirement 4). The one case the split does not cover is a user who has explicitly allowed this app's channel
	 * through Do Not Disturb in system settings: the channel then sounds under a priority filter that {@code AlertSounds}
	 * still reads as DND, and both play. The app cannot take that override back — it is the user's setting, and
	 * {@code setBypassDnd(false)} here would only restate the default the channel already has.
	 *
	 * @return the audio attributes for an alert channel's sound
	 */
	private static AudioAttributes alertAudioAttributes() {
		return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
	}

	private static NotificationManager getManager(Context context) {
		return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/**
	 * One alert channel's definition — the part of it that is the same at every settings version.
	 *
	 * @param baseId         the unversioned channel ID
	 * @param nameRes        the translated channel name
	 * @param descriptionRes the translated channel description
	 * @param ledColor       the LED colour for this channel's notifications
	 * @param soundKeyRes    the preference key holding the sound pick for this channel's severity bucket (#286)
	 */
	private record AlertChannel(String baseId, int nameRes, int descriptionRes, int ledColor, int soundKeyRes) {

		/**
		 * This channel as the system object to create, at a given settings version.
		 * <p>
		 * Re-calling {@code createNotificationChannel} with an existing ID updates only the name, description and group —
		 * Android ignores importance, vibration, lights and sound, so the user's (and the versioned #153) settings are
		 * preserved. That is how translated channel names reach <em>upgraded</em> installs, not just fresh ones (#165),
		 * and equally why a changed sound has to come back through {@link NotificationChannels#refreshAlertChannels}
		 * instead of being re-applied here on the next alert.
		 *
		 * @param context The application context
		 * @param prefs   the default SharedPreferences
		 * @param version the alert-channel settings version to create at
		 * @param vibrate whether the channel should vibrate (from the user's Vibrate preference)
		 *
		 * @return the channel to hand to {@code NotificationManager.createNotificationChannel}
		 */
		NotificationChannel toNotificationChannel(Context context, SharedPreferences prefs, int version, boolean vibrate) {
			final NotificationChannel channel = new NotificationChannel(versionedChannelId(baseId, version),
					context.getString(nameRes), NotificationManager.IMPORTANCE_HIGH);
			channel.setDescription(context.getString(descriptionRes));
			channel.enableLights(true);
			channel.setLightColor(ledColor);
			channel.enableVibration(vibrate);
			if (vibrate) {
				channel.setVibrationPattern(SystemService.VIBRATION_PATTERN);
			}
			channel.setSound(AlertSounds.soundUriOrSilent(alertSoundUri(context, prefs, baseId)), alertAudioAttributes());
			return channel;
		}
	}
}
