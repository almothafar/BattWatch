package com.almothafar.simplebatterynotifier.service;

import android.app.Notification;
import android.content.Context;
import android.os.BatteryManager;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.util.TemperatureUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The ongoing status notification (#192 title + #194 expandable body): the title is the stable
 * percentage + state; the <b>collapsed</b> content is rate/power · current · remaining; the
 * <b>expanded</b> content is a labelled Now / Average / time / temperature breakdown. Expected strings
 * are built from the same formatters the code uses, so the sign glyph, separators and units can't drift.
 * <p>
 * The text lives in {@link OngoingStatusContent}; the built {@link Notification} is assembled by
 * {@link NotificationService}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OngoingStatusContentTest {

	private static final String SEP = " · ";
	private Context context;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
	}

	private static BatteryDO discharging85() {
		return new BatteryDO().setLevel(85).setScale(100)
				.setStatus(BatteryManager.BATTERY_STATUS_DISCHARGING)
				.setTemperature(346);
	}

	private static BatteryDO charging80() {
		// 2 A × 5 V = 10 W.
		return new BatteryDO().setLevel(80).setScale(100)
				.setStatus(BatteryManager.BATTERY_STATUS_CHARGING)
				.setTemperature(346).setCurrentMicroAmps(2_000_000).setVoltage(5000);
	}

	/** Rate with a %/h only (no current/average yet) — the warm-up shape. */
	private static BatteryRateTracker.BatteryRate rate(boolean charging, int pph) {
		return new BatteryRateTracker.BatteryRate(true, pph, charging, false, 0, false, 0);
	}

	/** Rate with a %/h plus instantaneous and windowed-average current. */
	private static BatteryRateTracker.BatteryRate rateFull(boolean charging, int pph, int mA, int avgMa) {
		return new BatteryRateTracker.BatteryRate(true, pph, charging, true, mA, true, avgMa);
	}

	private String cur(int mA) {
		return BatteryRateTracker.formatCurrentValue(context, mA);
	}

	/** The worded collapsed current segment (#207): "Using 250 mA" / "Charging 2000 mA", unsigned magnitude. */
	private String curCollapsed(boolean charging, int mA) {
		final String magnitude = context.getString(R.string.battery_current_value, String.valueOf(Math.abs(mA)));
		return context.getString(charging
				? R.string.notification_status_current_charging
				: R.string.notification_status_current_using, magnitude);
	}

	private String line(int labelRes, String value) {
		return context.getString(R.string.notification_detail_line, context.getString(labelRes), value);
	}

	private String temp() {
		return TemperatureUtils.format(context, 346);
	}

	@Test
	public void title_isStableHeadline_notAppNameNorRate() {
		final Notification built = NotificationService.buildOngoingNotification(context, discharging85(), rate(false, 9));
		assertEquals("85% · Discharging", String.valueOf(built.extras.getCharSequence(Notification.EXTRA_TITLE)));
	}

	@Test
	public void collapsed_isRateCurrentRemaining_whileDischarging() {
		// 85% at 9 %/h → 566.67 → 567 min → "~9h 27m".
		final String remaining = context.getString(R.string.notification_status_time_remaining, "~9h 27m");
		final String expected = "9%/h" + SEP + curCollapsed(false, -250) + SEP + remaining;
		assertEquals(expected, OngoingStatusContent.statusDetail(context, discharging85(), rateFull(false, 9, -250, -338)));
	}

	@Test
	public void collapsed_isWattsCurrentToFull_whileCharging() {
		// 20 points to full at 20 %/h → exactly 60 min → "~1h 0m".
		final String toFull = context.getString(R.string.notification_status_time_to_full, "~1h 0m");
		final String expected = "~10 W" + SEP + curCollapsed(true, 2000) + SEP + toFull;
		assertEquals(expected, OngoingStatusContent.statusDetail(context, charging80(), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void collapsed_dropsCurrent_whenNoneYet() {
		// Warm-up: %/h available, no current → rate · remaining, no mA segment.
		final String remaining = context.getString(R.string.notification_status_time_remaining, "~9h 27m");
		assertEquals("9%/h" + SEP + remaining,
				OngoingStatusContent.statusDetail(context, discharging85(), rate(false, 9)));
	}

	@Test
	public void collapsed_wordsLoneCurrent_whenNoRateYet() {
		// #207: warm-up with an instantaneous current but no rate/time — the segment that used to read as a
		// bare, meaningless "−199 mA". It must now stand alone as the worded "Using 199 mA".
		final BatteryRateTracker.BatteryRate loneCurrent =
				new BatteryRateTracker.BatteryRate(false, 0, false, true, -199, false, 0);
		assertEquals(curCollapsed(false, -199),
				OngoingStatusContent.statusDetail(context, discharging85(), loneCurrent));
	}

	@Test
	public void expanded_isLabelledBreakdown_whileDischarging() {
		final String expected = String.join("\n",
				line(R.string.notification_label_now, cur(-250)),
				line(R.string.notification_label_average, cur(-338) + SEP + "9%/h"),
				line(R.string.time_remaining, "~9h 27m"),
				line(R.string.temperature, temp()));
		assertEquals(expected, OngoingStatusContent.statusDetailExpanded(context, discharging85(), rateFull(false, 9, -250, -338)));
	}

	@Test
	public void expanded_isLabelledBreakdown_whileCharging() {
		// Now pairs current with wattage; Average is current only (no %/h while charging).
		final String expected = String.join("\n",
				line(R.string.notification_label_now, cur(2000) + SEP + "~10 W"),
				line(R.string.notification_label_average, cur(1900)),
				line(R.string.time_to_full, "~1h 0m"),
				line(R.string.temperature, temp()));
		assertEquals(expected, OngoingStatusContent.statusDetailExpanded(context, charging80(), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void expanded_usesDrainRateLine_whenNoAverageCurrentYet() {
		// %/h but no average current → the rate gets its own labelled line instead of riding Average.
		final String expected = String.join("\n",
				line(R.string.drain_rate, "9%/h"),
				line(R.string.time_remaining, "~9h 27m"),
				line(R.string.temperature, temp()));
		assertEquals(expected, OngoingStatusContent.statusDetailExpanded(context, discharging85(), rate(false, 9)));
	}

	@Test
	public void builtNotification_isExpandable_whenBreakdownAvailable() {
		final Notification built = NotificationService.buildOngoingNotification(context, discharging85(), rateFull(false, 9, -250, -338));
		final CharSequence bigText = built.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
		assertEquals(OngoingStatusContent.statusDetailExpanded(context, discharging85(), rateFull(false, 9, -250, -338)),
				String.valueOf(bigText));
	}

	@Test
	public void rateDisplayOff_showsTemperatureOnly_andNotExpandable() {
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putBoolean(context.getString(R.string._pref_key_show_rate_in_notification), false)
				.commit();
		// Collapsed: bare temperature. Expanded: a single "Temperature: …" line → no BigText.
		assertEquals(temp(), OngoingStatusContent.statusDetail(context, discharging85(), rateFull(false, 9, -250, -338)));
		assertEquals(line(R.string.temperature, temp()),
				OngoingStatusContent.statusDetailExpanded(context, discharging85(), rateFull(false, 9, -250, -338)));
		final Notification built = NotificationService.buildOngoingNotification(context, discharging85(), rateFull(false, 9, -250, -338));
		assertNull(built.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
	}

	@Test
	public void nullSnapshot_yieldsUnknownTitleAndEmptyDetail() {
		assertEquals("0% · Unknown", OngoingStatusContent.statusTitle(context, null));
		assertEquals("", OngoingStatusContent.statusDetail(context, null, null));
		assertEquals("", OngoingStatusContent.statusDetailExpanded(context, null, null));
	}

	// --- Level-mirroring small icon (#235) -------------------------------------------------------

	/** The ongoing small icon at a given level and {@code BatteryManager.BATTERY_STATUS_*} state. */
	private static int iconAt(int level, int status) {
		return OngoingStatusContent.ongoingIconRes(new BatteryDO().setLevel(level).setScale(100).setStatus(status));
	}

	/** Asserts the icon at both ends of a band, so a shifted boundary can't slip through. */
	private static void assertBand(
			int lowLevel,
			int highLevel,
			int status,
			int expectedIcon) {
		assertEquals("level " + lowLevel, expectedIcon, iconAt(lowLevel, status));
		assertEquals("level " + highLevel, expectedIcon, iconAt(highLevel, status));
	}

	@Test
	public void dischargingIcon_mirrorsLevel_atEveryBandBoundary() {
		final int discharging = BatteryManager.BATTERY_STATUS_DISCHARGING;
		assertBand(0, 9, discharging, R.drawable.ic_stat_battery_0_bar);
		assertBand(10, 24, discharging, R.drawable.ic_stat_battery_1_bar);
		assertBand(25, 39, discharging, R.drawable.ic_stat_battery_2_bar);
		assertBand(40, 54, discharging, R.drawable.ic_stat_battery_3_bar);
		assertBand(55, 69, discharging, R.drawable.ic_stat_battery_4_bar);
		assertBand(70, 84, discharging, R.drawable.ic_stat_battery_5_bar);
		assertBand(85, 99, discharging, R.drawable.ic_stat_battery_6_bar);
		assertBand(100, 100, discharging, R.drawable.ic_stat_battery_full);
	}

	@Test
	public void chargingIcon_mirrorsLevel_atEveryBandBoundary() {
		final int charging = BatteryManager.BATTERY_STATUS_CHARGING;
		assertBand(0, 24, charging, R.drawable.ic_stat_battery_charging_20);
		assertBand(25, 39, charging, R.drawable.ic_stat_battery_charging_30);
		assertBand(40, 54, charging, R.drawable.ic_stat_battery_charging_50);
		assertBand(55, 72, charging, R.drawable.ic_stat_battery_charging_60);
		assertBand(73, 87, charging, R.drawable.ic_stat_battery_charging_80);
		assertBand(88, 97, charging, R.drawable.ic_stat_battery_charging_90);
		assertBand(98, 100, charging, R.drawable.ic_stat_battery_charging_full);
	}

	@Test
	public void icon_distinguishesChargingFromDischarging_atTheSameLevel() {
		// Same level, different state → different artwork; the bolt is the only cue for "actively charging".
		assertEquals(R.drawable.ic_stat_battery_6_bar, iconAt(90, BatteryManager.BATTERY_STATUS_DISCHARGING));
		assertEquals(R.drawable.ic_stat_battery_charging_90, iconAt(90, BatteryManager.BATTERY_STATUS_CHARGING));
	}

	@Test
	public void icon_fallsBackToFull_whenSnapshotUnavailable() {
		assertEquals(R.drawable.ic_stat_battery_full, OngoingStatusContent.ongoingIconRes(null));
	}

	@Test
	public void icon_readsAsFull_whenChargedButStillPlugged() {
		// BATTERY_STATUS_FULL is not CHARGING, so it takes the discharging ladder: a solid battery, no bolt.
		assertEquals(R.drawable.ic_stat_battery_full, iconAt(100, BatteryManager.BATTERY_STATUS_FULL));
	}

	@Test
	public void icon_holdsTheFloor_whenLevelIsNonsensical() {
		// The ladders end at a zero floor, so a negative level can't fall out of the loop untranslated.
		assertEquals(R.drawable.ic_stat_battery_0_bar, iconAt(-1, BatteryManager.BATTERY_STATUS_DISCHARGING));
		assertEquals(R.drawable.ic_stat_battery_charging_20, iconAt(-1, BatteryManager.BATTERY_STATUS_CHARGING));
	}
}
