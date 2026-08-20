package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.util.AppPrefs;
import com.almothafar.simplebatterynotifier.util.BatteryPercentFormatter;

import static com.almothafar.simplebatterynotifier.util.BidiText.isolate;

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
	 * @param context      The application context
	 * @param prefs        SharedPreferences containing user settings
	 * @param type         Which battery-level alert to configure (non-null)
	 * @param levelPercent The battery level the alert fired at; only the full-battery alert's copy
	 *                     depends on it (#263)
	 */
	NotificationConfig(Context context, SharedPreferences prefs, AlertType type, int levelPercent) {
		this.type = type;

		this.stickyNotification = prefs.getBoolean(context.getString(R.string._pref_key_notifications_sticky), false);
		final boolean withinWindow = QuietHours.isWithinNotificationWindow(context, prefs);
		final boolean criticalIgnoresQuietHours = prefs.getBoolean(context.getString(R.string._pref_key_critical_ignore_quiet_hours), true);
		this.alertsAllowed = QuietHours.alertsAllowedNow(withinWindow, type == AlertType.CRITICAL, criticalIgnoresQuietHours);
		this.ignoreSilent = QuietHours.shouldIgnoreSilentMode(context, prefs);
		this.vibrate = AppPrefs.vibrateEnabled(context);

		// A switch EXPRESSION over the enum is exhaustive at compile time — the old int switch
		// needed a default branch, which posted a completely blank notification on any invalid
		// type value (issue #160). That branch can no longer exist.
		//
		// Each branch reads only its own threshold, so the full-battery alert — the one that fires at the end of every charge — does no preference lookups for
		// levels it never names.
		final AlertStyle style = switch (type) {
			case CRITICAL -> criticalAlertStyle(context);
			case WARNING -> warningAlertStyle(context);
			case FULL -> fullAlertStyle(context, levelPercent);
		};
		this.channelId = style.channelId();
		this.iconRes = style.iconRes();
		// The channel this alert posts on is what decides which sound pick applies, so the override-silent playback below can't
		// end up on a different sound from the one the channel itself plays (#286).
		this.alarmSound = NotificationChannels.alertSoundUri(context, this.channelId);
		this.ticker = style.ticker();
		this.title = style.title();
		this.content = style.content();
		this.bigContent = style.bigContent();
	}

	/**
	 * The critical alert's presentation: the copy names the threshold the user set, not the level the battery is at.
	 * <p>
	 * The level is formatted here rather than handed to {@code getString} as an int. {@code getString} formats with the <b>configuration</b> locale, so a
	 * {@code %1$d} placeholder renders {@code ٢٠} under ar-EG/ar-SA/ar-JO with no {@code String.format} call anywhere in our code (#273);
	 * {@link BatteryPercentFormatter#formatWhole} keeps the digits Western and brings the {@code '%'} sign with it.
	 * <p>
	 * That sign is why the level is isolated before it goes into the copy (#275): every string below sets it against Arabic prose, where a bare {@code "20%"}
	 * renders {@code %20}. Isolating the formatted level — not the digits alone — keeps the sign on the number it belongs to.
	 *
	 * @param context The application context
	 *
	 * @return the copy and channel for this alert
	 */
	private static AlertStyle criticalAlertStyle(Context context) {
		final String levelText = isolate(BatteryPercentFormatter.formatWhole(AppPrefs.criticalLevel(context)));
		return new AlertStyle(
				NotificationChannels.CHANNEL_ID_CRITICAL,
				R.drawable.ic_stat_battery_alert,
				context.getString(R.string.notification_critical_ticker, levelText),
				context.getString(R.string.notification_critical_title),
				context.getString(R.string.notification_critical_content, levelText),
				context.getString(R.string.notification_critical_content_big, levelText));
	}

	/**
	 * The warning alert's presentation — the critical alert's shape one threshold up, on its own channel and sound.
	 *
	 * @param context The application context
	 *
	 * @return the copy and channel for this alert
	 */
	private static AlertStyle warningAlertStyle(Context context) {
		final String levelText = isolate(BatteryPercentFormatter.formatWhole(AppPrefs.warningLevel(context)));
		return new AlertStyle(
				NotificationChannels.CHANNEL_ID_WARNING,
				R.drawable.ic_stat_battery_low,
				context.getString(R.string.notification_warning_ticker, levelText),
				context.getString(R.string.notification_warning_title),
				context.getString(R.string.notification_warning_content, levelText),
				context.getString(R.string.notification_warning_content_big, levelText));
	}

	/**
	 * The full-battery alert's presentation, which follows the user's charge target (#263).
	 * <p>
	 * The "almost full" copy is used only when the alert actually fired on the target — the level reached it. Below a full charge the battery is not full, so
	 * "Battery fully charged" would be a plain lie; but the mirror image is a lie too, and the alert has a second trigger that can produce it. A device whose
	 * charge cap reports a completed charge short of the target (Samsung's "Protect battery" at 85%, Sony and Asus at 80) fires below it, and announcing
	 * "reached your 90% charge target" at 85% states something that did not happen. Keying on the level rather than on the preference alone keeps both wordings
	 * true on every path: a completed charge is reported as a completed charge, whatever the target says.
	 * <p>
	 * At the maximum target that is the only reachable path, so the original {@code notification_full_level_*} copy is used verbatim there. Two string sets
	 * rather than one parameterised one — the messages differ in more than a number.
	 *
	 * @param context      The application context
	 * @param levelPercent the battery level the alert fired at
	 *
	 * @return the copy and channel for this alert
	 */
	private static AlertStyle fullAlertStyle(Context context, int levelPercent) {
		final int target = AppPrefs.chargeTarget(context);
		// Below the target the only way here is a charge the battery reported as complete.
		final boolean chargeIsComplete = AppPrefs.targetIsAFullCharge(target) || levelPercent < target;

		final String ticker;
		final String title;
		final String content;
		final String bigContent;
		if (chargeIsComplete) {
			ticker = context.getString(R.string.notification_full_level_ticker);
			title = context.getString(R.string.notification_full_level_title);
			content = context.getString(R.string.notification_full_level_content);
			bigContent = context.getString(R.string.notification_full_level_content_big);
		} else {
			// Two different numbers, deliberately: the ticker reports the level the battery actually reached, the body names the target that level crossed.
			// Plugging in at 95% with a target of 90 is "95% reached" against "your 90% charge target", and both are true.
			//
			// Both go through BatteryPercentFormatter rather than a %1$d placeholder: getString formats with the configuration locale, so a bare int prints ٩٥
			// on ar-EG/ar-SA/ar-JO. Numbers stay Western in every locale (#96), and are isolated so the '%' the formatter brings with them stays on their side
			// of the Arabic prose these two are set into (#275).
			final String reachedText = isolate(BatteryPercentFormatter.formatWhole(levelPercent));
			final String targetText = isolate(BatteryPercentFormatter.formatWhole(target));

			ticker = context.getString(R.string.notification_almost_full_ticker, reachedText);
			title = context.getString(R.string.notification_almost_full_title);
			content = context.getString(R.string.notification_almost_full_content, targetText);
			bigContent = context.getString(R.string.notification_almost_full_content_big, targetText);
		}

		return new AlertStyle(
				NotificationChannels.CHANNEL_ID_FULL,
				R.drawable.ic_stat_battery_full,
				ticker,
				title,
				content,
				bigContent);
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
	 * @param ticker     the ticker text
	 * @param title      the notification title
	 * @param content    the collapsed content line
	 * @param bigContent the expanded (BigTextStyle) content
	 */
	private record AlertStyle(
			String channelId,
			int iconRes,
			String ticker,
			String title,
			String content,
			String bigContent) {
	}
}
