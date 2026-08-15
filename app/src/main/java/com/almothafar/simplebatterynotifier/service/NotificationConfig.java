package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.util.AppPrefs;

/**
 * Configuration for a battery-level notification (reduces parameter count).
 * <p>
 * Encapsulates everything needed to build a level alert, extracted from SharedPreferences and the alert
 * type. Split out of {@code NotificationService} (issue #166) so the dispatch layer holds the flow and
 * this holds the per-type data. Its display half is turned into an {@link AlertSpec} by the caller so the
 * level alert flows through the same builder chain as the other alerts.
 */
final class NotificationConfig {
	final AlertType type;
	final String channelId;
	final int iconRes;
	final String ticker;
	final String title;
	final String content;
	final String bigContent;
	final String alarmSound;
	final boolean alertsAllowed;
	final boolean ignoreSilent;
	final boolean vibrate;
	final boolean stickyNotification;

	/**
	 * Create a NotificationConfig from preferences and type
	 *
	 * @param context The application context
	 * @param prefs   SharedPreferences containing user settings
	 * @param type    Which battery-level alert to configure (non-null)
	 */
	NotificationConfig(Context context, SharedPreferences prefs, AlertType type) {
		this.type = type;

		// Load common preferences
		final int warningLevel = AppPrefs.warningLevel(context);
		final int criticalLevel = AppPrefs.criticalLevel(context);

		this.stickyNotification = prefs.getBoolean(context.getString(R.string._pref_key_notifications_sticky), false);
		final boolean withinWindow = QuietHours.isWithinNotificationWindow(context, prefs);
		final boolean criticalIgnoresQuietHours = prefs.getBoolean(context.getString(R.string._pref_key_critical_ignore_quiet_hours), true);
		this.alertsAllowed = QuietHours.alertsAllowedNow(withinWindow, type == AlertType.CRITICAL, criticalIgnoresQuietHours);
		this.ignoreSilent = QuietHours.shouldIgnoreSilentMode(context, prefs);
		this.vibrate = AppPrefs.vibrateEnabled(context);

		final String defaultSound = context.getString(R.string._default_notification_sound_uri);

		// A switch EXPRESSION over the enum is exhaustive at compile time — the old int switch
		// needed a default branch, which posted a completely blank notification on any invalid
		// type value (issue #160). That branch can no longer exist.
		final AlertStyle style = switch (type) {
			case CRITICAL -> new AlertStyle(
					NotificationChannels.CHANNEL_ID_CRITICAL,
					R.drawable.ic_stat_battery_alert,
					prefs.getString(context.getString(R.string._pref_key_notifications_alert_sound_ringtone), defaultSound),
					context.getString(R.string.notification_critical_ticker, criticalLevel),
					context.getString(R.string.notification_critical_title),
					context.getString(R.string.notification_critical_content, criticalLevel),
					context.getString(R.string.notification_critical_content_big, criticalLevel));
			case WARNING -> new AlertStyle(
					NotificationChannels.CHANNEL_ID_WARNING,
					R.drawable.ic_stat_battery_low,
					prefs.getString(context.getString(R.string._pref_key_notifications_warning_sound_ringtone), defaultSound),
					context.getString(R.string.notification_warning_ticker, warningLevel),
					context.getString(R.string.notification_warning_title),
					context.getString(R.string.notification_warning_content, warningLevel),
					context.getString(R.string.notification_warning_content_big, warningLevel));
			case FULL -> fullAlertStyle(context, prefs, defaultSound);
		};
		this.channelId = style.channelId();
		this.iconRes = style.iconRes();
		this.alarmSound = style.alarmSound();
		this.ticker = style.ticker();
		this.title = style.title();
		this.content = style.content();
		this.bigContent = style.bigContent();
	}

	/**
	 * The full-battery alert's presentation, which follows the user's charge target (#263).
	 * <p>
	 * Below a full charge the battery is <em>not</em> full, so "Battery fully charged" would be a plain
	 * lie: the alert says the target was reached and names it. At the maximum target the original
	 * {@code notification_full_level_*} copy is used verbatim, so the behaviour of an install that never
	 * touches the slider is unchanged. Two string sets rather than one parameterised one — the messages
	 * differ in more than a number.
	 *
	 * @param context      The application context
	 * @param prefs        SharedPreferences containing user settings
	 * @param defaultSound the fallback alarm sound URI
	 *
	 * @return the copy and channel for this alert
	 */
	private static AlertStyle fullAlertStyle(Context context, SharedPreferences prefs, String defaultSound) {
		final int target = AppPrefs.chargeTarget(context);
		final String sound = prefs.getString(
				context.getString(R.string._pref_key_notifications_full_sound_ringtone), defaultSound);

		if (AppPrefs.targetIsAFullCharge(target)) {
			return new AlertStyle(
					NotificationChannels.CHANNEL_ID_FULL,
					R.drawable.ic_stat_battery_full,
					sound,
					context.getString(R.string.notification_full_level_ticker),
					context.getString(R.string.notification_full_level_title),
					context.getString(R.string.notification_full_level_content),
					context.getString(R.string.notification_full_level_content_big));
		}
		return new AlertStyle(
				NotificationChannels.CHANNEL_ID_FULL,
				R.drawable.ic_stat_battery_full,
				sound,
				context.getString(R.string.notification_almost_full_ticker, target),
				context.getString(R.string.notification_almost_full_title),
				context.getString(R.string.notification_almost_full_content, target),
				context.getString(R.string.notification_almost_full_content_big, target));
	}

	/**
	 * The display half of this config as an {@link AlertSpec}, so the level alert flows through the same
	 * builder chain as the other alerts (issue #166) without the dispatcher reaching into these fields.
	 * The audible base channel is this config's own channel; the caller supplies the level alert's id.
	 *
	 * @param notificationId the level alert's notification id
	 * @return the display spec for this level alert
	 */
	AlertSpec toAlertSpec(int notificationId) {
		return new AlertSpec("level", channelId, notificationId, iconRes, ticker, title, content, bigContent);
	}

	/**
	 * The per-type presentation of a battery-level alert, produced by the exhaustive type switch above
	 * (#160).
	 *
	 * @param channelId  the audible base channel ID for this type
	 * @param iconRes    the small-icon resource
	 * @param alarmSound the user's chosen alarm sound for this type
	 * @param ticker     the ticker text
	 * @param title      the notification title
	 * @param content    the collapsed content line
	 * @param bigContent the expanded (BigTextStyle) content
	 */
	private record AlertStyle(String channelId, int iconRes, String alarmSound, String ticker,
	                          String title, String content, String bigContent) {
	}
}
