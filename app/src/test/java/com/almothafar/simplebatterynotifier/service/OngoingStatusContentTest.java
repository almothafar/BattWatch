package com.almothafar.simplebatterynotifier.service;

import android.app.Notification;
import android.content.Context;
import android.os.BatteryManager;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.BatteryDO;
import com.almothafar.simplebatterynotifier.model.DetailFamily;
import com.almothafar.simplebatterynotifier.util.AppPrefs;
import com.almothafar.simplebatterynotifier.util.TemperatureUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
		return new BatteryDO().setLevel(80)
		                      .setScale(100)
		                      .setStatus(BatteryManager.BATTERY_STATUS_CHARGING)
		                      .setPlugged(BatteryManager.BATTERY_PLUGGED_AC)
		                      .setTemperature(346)
		                      .setCurrentMicroAmps(2_000_000)
		                      .setVoltage(5000);
	}

	/** Rate with a %/h but no current or average: a phone that reports no usable current, so none is coming (#359). */
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
		// A %/h but no current → rate · remaining, no mA segment.
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

	// --- Notification details: the user-chosen families (#357) ----------------------------------

	private void select(DetailFamily... families) {
		AppPrefs.setNotificationDetails(context, List.of(families));
	}

	/** The collapsed line with only {@code family} chosen. */
	private String alone(DetailFamily family, BatteryDO batteryDO, BatteryRateTracker.BatteryRate rate) {
		select(family);
		return OngoingStatusContent.statusDetail(context, batteryDO, rate);
	}

	@Test
	public void speed_isTheDrainRate_whileDischarging_andTheWattage_whileCharging() {
		assertEquals("9%/h", alone(DetailFamily.SPEED, discharging85(), rateFull(false, 9, -250, -338)));
		assertEquals("~10 W", alone(DetailFamily.SPEED, charging80(), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void speed_fallsBackToTheChargeRate_whenTheWattageIsUnknown() {
		// No current and no voltage → no wattage estimate, so the %/h stands in (the pre-#357 behaviour).
		final BatteryDO noWattage = new BatteryDO().setLevel(80).setScale(100).setStatus(BatteryManager.BATTERY_STATUS_CHARGING).setTemperature(346);
		assertEquals("20%/h", alone(DetailFamily.SPEED, noWattage, rate(true, 20)));
	}

	@Test
	public void current_isWorded_inBothDirections() {
		assertEquals(curCollapsed(false, -250), alone(DetailFamily.CURRENT, discharging85(), rateFull(false, 9, -250, -338)));
		assertEquals(curCollapsed(true, 2000), alone(DetailFamily.CURRENT, charging80(), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void time_isRemaining_whileDischarging_andToFull_whileCharging() {
		assertEquals(context.getString(R.string.notification_status_time_remaining, "~9h 27m"),
				alone(DetailFamily.TIME, discharging85(), rateFull(false, 9, -250, -338)));
		assertEquals(context.getString(R.string.notification_status_time_to_full, "~1h 0m"),
				alone(DetailFamily.TIME, charging80(), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void temperature_isTheSame_inBothDirections() {
		assertEquals(temp(), alone(DetailFamily.TEMPERATURE, discharging85(), rateFull(false, 9, -250, -338)));
		assertEquals(temp(), alone(DetailFamily.TEMPERATURE, charging80(), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void powerSource_isOnBattery_whileUnplugged_andTheChargerType_whilePlugged() {
		assertEquals("On battery", alone(DetailFamily.POWER_SOURCE, discharging85(), rateFull(false, 9, -250, -338)));
		// Not "AC": many power banks and fast chargers report BATTERY_PLUGGED_AC too, so the line claims no wall socket (#122).
		assertEquals("Charger", alone(DetailFamily.POWER_SOURCE, charging80(), rateFull(true, 20, 2000, 1900)));
		assertEquals("USB", alone(DetailFamily.POWER_SOURCE, charging80().setPlugged(BatteryManager.BATTERY_PLUGGED_USB), rateFull(true, 20, 2000, 1900)));
		assertEquals("Wireless",
				alone(DetailFamily.POWER_SOURCE, charging80().setPlugged(BatteryManager.BATTERY_PLUGGED_WIRELESS), rateFull(true, 20, 2000, 1900)));
		// BATTERY_PLUGGED_DOCK (8) arrived in API 33, after the app's charger names were written.
		assertEquals("Plugged in", alone(DetailFamily.POWER_SOURCE, charging80().setPlugged(8), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void aFamilyWithNoDataYet_dropsOutSilently() {
		// No current, so Current drops out and its neighbours close up around it.
		select(DetailFamily.SPEED, DetailFamily.CURRENT, DetailFamily.POWER_SOURCE);
		assertEquals("9%/h" + SEP + "On battery", OngoingStatusContent.statusDetail(context, discharging85(), rate(false, 9)));
	}

	@Test
	@Config(qualifiers = "ar-rEG")
	public void arabic_keepsWesternDigits_inEveryFamily() {
		// Premise: a region-bearing Arabic locale really localises digits. Bare "ar" picks Western ones and would pass against the defect (#241).
		assertEquals("ar-rEG should select Eastern Arabic digits", "٤٠", String.format(Locale.getDefault(), "%d", 40));

		select(DetailFamily.SPEED, DetailFamily.CURRENT, DetailFamily.TIME);
		final String dischargingLine = OngoingStatusContent.statusDetail(context, discharging85(), rateFull(false, 9, -250, -338));
		final String chargingLine = OngoingStatusContent.statusDetail(context, charging80(), rateFull(true, 20, 2000, 1900));
		select(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE);
		final String staticLine = OngoingStatusContent.statusDetail(context, charging80(), rateFull(true, 20, 2000, 1900));

		// values-ar was really loaded, so these are assertions about the Arabic copy and not an English fallback.
		assertTrue(dischargingLine, dischargingLine.contains("استهلاك"));
		assertTrue(chargingLine, chargingLine.contains("واط") && chargingLine.contains("حتى الاكتمال"));
		assertTrue(staticLine, staticLine.contains("شاحن"));
		// "Charger", not "wall charger": power banks report AC too (#122).
		assertFalse(staticLine, staticLine.contains("جداري"));
		for (final String line : List.of(dischargingLine, chargingLine, staticLine)) {
			assertFalse(line, line.matches(".*[٠-٩].*"));
		}
		assertTrue(dischargingLine, dischargingLine.contains("250") && dischargingLine.contains("27"));
		assertTrue(chargingLine, chargingLine.contains("10") && chargingLine.contains("2000"));
		assertTrue(staticLine, staticLine.contains("34.6"));
	}

	@Test
	public void aChoiceWithNothingToShowYet_readsCalculating_notTheTemperature() {
		// The temperature is a family the user can choose now, so showing it unbidden would override a choice that left it out (#359).
		select(DetailFamily.CURRENT, DetailFamily.TIME);
		assertEquals("Calculating…", OngoingStatusContent.statusDetail(context, discharging85(), BatteryRateTracker.BatteryRate.empty()));
	}

	@Test
	public void aChoiceWithNothingComing_readsNotAvailable() {
		// A phone that reports no current: the rate is in, so Current alone has nothing coming, and "Calculating…" would promise it.
		select(DetailFamily.CURRENT);
		assertEquals("Not available", OngoingStatusContent.statusDetail(context, discharging85(), rate(false, 9)));
	}

	@Test
	public void aFullBattery_readsNotAvailable_notCalculating() {
		// Full on USB, as the Mate 10 Pro sits: no current, and a level that no longer moves, so no rate either. None comes while it stays full, and
		// "Calculating…" would stand there all night.
		final BatteryRateTracker.BatteryRate noRate = new BatteryRateTracker.BatteryRate(false, 0, true, false, 0, false, 0);
		final BatteryDO full = new BatteryDO().setLevel(100)
		                                      .setScale(100)
		                                      .setStatus(BatteryManager.BATTERY_STATUS_FULL)
		                                      .setPlugged(BatteryManager.BATTERY_PLUGGED_USB);
		select(DetailFamily.SPEED, DetailFamily.CURRENT, DetailFamily.TIME);
		assertEquals("Not available", OngoingStatusContent.statusDetail(context, full, noRate));
		// Still charging at 100%, as some phones report before they say full: no time left to estimate either.
		assertEquals("Not available", OngoingStatusContent.statusDetail(context, full.setStatus(BatteryManager.BATTERY_STATUS_CHARGING), noRate));
		// One point short of full, the same readings are a rate still settling.
		assertEquals("Calculating…", OngoingStatusContent.statusDetail(context, full.setLevel(99), noRate));
	}

	@Test
	public void aTimeEstimateWithNoTimeLeft_readsNotAvailable() {
		// Discharging at 0%, where some phones hold for a while before they shut down: the rate is in, but there is no time left to estimate. The details
		// table shows its dash there too (#216).
		select(DetailFamily.TIME);
		assertEquals("Not available", OngoingStatusContent.statusDetail(context, discharging85().setLevel(0), rate(false, 9)));
	}

	@Test
	public void aChoiceWithOneValueStillComing_readsCalculating() {
		// No family today can be on its way while another has none coming: both hang on the rate. A family added later can (#193, #354), and then one value
		// still coming keeps the line at "Calculating…".
		final List<DetailFamily> choice = List.of(DetailFamily.CURRENT, DetailFamily.TIME);
		assertEquals("Calculating…", OngoingStatusContent.detailLine(context, choice, family -> null, family -> family == DetailFamily.CURRENT));
		assertEquals("Not available", OngoingStatusContent.detailLine(context, choice, family -> null, family -> true));
	}

	@Test
	public void aChosenTemperature_stillShowsOnAWarmUpTick() {
		// Chosen, not a fallback: it has a reading from the first tick, so the line is never "Calculating…" while it is in the choice.
		select(DetailFamily.TEMPERATURE);
		assertEquals(temp(), OngoingStatusContent.statusDetail(context, discharging85(), BatteryRateTracker.BatteryRate.empty()));
		select(DetailFamily.SPEED, DetailFamily.TEMPERATURE);
		assertEquals(temp(), OngoingStatusContent.statusDetail(context, discharging85(), BatteryRateTracker.BatteryRate.empty()));
	}

	@Test
	public void anEmptyChoice_rendersAnEmptyLine() {
		select();
		assertEquals("", OngoingStatusContent.statusDetail(context, discharging85(), rateFull(false, 9, -250, -338)));
	}

	@Test
	public void collapsed_followsTheStoredOrder_notTheRegistryOrder() {
		select(DetailFamily.TIME, DetailFamily.SPEED);
		final String remaining = context.getString(R.string.notification_status_time_remaining, "~9h 27m");
		assertEquals(remaining + SEP + "9%/h", OngoingStatusContent.statusDetail(context, discharging85(), rateFull(false, 9, -250, -338)));
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

	/** Stores a value for the show-rate switch #357 removed, as an existing install would have it. */
	private void legacyShowRate(boolean on) {
		PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(context.getString(R.string._pref_key_show_rate_in_notification), on).commit();
	}

	@Test
	public void legacyRateSwitchOff_carriesOverAsTemperatureOnly() {
		legacyShowRate(false);
		// What the switch showed while off: the bare temperature, even with a full rate available.
		assertEquals(temp(), OngoingStatusContent.statusDetail(context, discharging85(), rateFull(false, 9, -250, -338)));
		assertEquals(temp(), OngoingStatusContent.statusDetail(context, charging80(), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void legacyRateSwitchOff_keepsItsLine_butGetsTheFullPullDown() {
		// The switch hid the pull-down's rate lines too. Only the line carries over: the pull-down is the everything view for everyone (docs/adr/0002).
		legacyShowRate(false);
		final Notification built = NotificationService.buildOngoingNotification(context, discharging85(), rateFull(false, 9, -250, -338));
		assertEquals(temp(), String.valueOf(built.extras.getCharSequence(Notification.EXTRA_TEXT)));
		final String pullDown = String.valueOf(built.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
		assertEquals(pullDown, 4, pullDown.split("\n").length);
		assertTrue(pullDown, pullDown.startsWith(line(R.string.notification_label_now, cur(-250))));
	}

	@Test
	public void legacyRateSwitchOn_carriesOverAsSpeedCurrentTime() {
		legacyShowRate(true);
		final String remaining = context.getString(R.string.notification_status_time_remaining, "~9h 27m");
		assertEquals("9%/h" + SEP + curCollapsed(false, -250) + SEP + remaining,
				OngoingStatusContent.statusDetail(context, discharging85(), rateFull(false, 9, -250, -338)));
		final String toFull = context.getString(R.string.notification_status_time_to_full, "~1h 0m");
		assertEquals("~10 W" + SEP + curCollapsed(true, 2000) + SEP + toFull,
				OngoingStatusContent.statusDetail(context, charging80(), rateFull(true, 20, 2000, 1900)));
	}

	@Test
	public void savedChoice_supersedesTheLegacySwitch() {
		legacyShowRate(false);
		select(DetailFamily.SPEED);
		assertEquals("9%/h", OngoingStatusContent.statusDetail(context, discharging85(), rateFull(false, 9, -250, -338)));
	}

	@Test
	public void expanded_ignoresTheChoice_andStaysTheFullBreakdown() {
		// docs/adr/0002: the expanded view is the everything view, so narrowing the collapsed line must not narrow it.
		select(DetailFamily.TEMPERATURE);
		final Notification built = NotificationService.buildOngoingNotification(context, discharging85(), rateFull(false, 9, -250, -338));
		assertEquals(OngoingStatusContent.statusDetailExpanded(context, discharging85(), rateFull(false, 9, -250, -338)),
				String.valueOf(built.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)));
		assertEquals(4, String.valueOf(built.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)).split("\n").length);
	}

	@Test
	public void aWarmUpPullDown_opensOntoTheTemperature_whenTheLineLeavesItOut() {
		// Right after a plug change nothing is measured yet, so the pull-down is the temperature alone. A line still showing another family puts the
		// temperature nowhere else, so that one line earns its expand chevron after all.
		select(DetailFamily.POWER_SOURCE);
		final Notification built = NotificationService.buildOngoingNotification(context, discharging85(), BatteryRateTracker.BatteryRate.empty());
		assertEquals("On battery", String.valueOf(built.extras.getCharSequence(Notification.EXTRA_TEXT)));
		assertEquals(line(R.string.temperature, temp()), String.valueOf(built.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)));

		// A line that reads "Calculating…" leaves it out as well, so the pull-down is still the everything view there (#359).
		select(DetailFamily.SPEED, DetailFamily.CURRENT, DetailFamily.TIME);
		final Notification calculating = NotificationService.buildOngoingNotification(context, discharging85(), BatteryRateTracker.BatteryRate.empty());
		assertEquals("Calculating…", String.valueOf(calculating.extras.getCharSequence(Notification.EXTRA_TEXT)));
		assertEquals(line(R.string.temperature, temp()), String.valueOf(calculating.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)));
	}

	@Test
	public void aWarmUpPullDown_staysShut_whenTheLineAlreadyShowsTheTemperature() {
		// A chevron would open onto the reading the line already shows (#194).
		select(DetailFamily.TEMPERATURE, DetailFamily.POWER_SOURCE);
		final Notification chosen = NotificationService.buildOngoingNotification(context, discharging85(), BatteryRateTracker.BatteryRate.empty());
		assertEquals(temp() + SEP + "On battery", String.valueOf(chosen.extras.getCharSequence(Notification.EXTRA_TEXT)));
		assertNull(chosen.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
	}

	@Test
	public void noReadingYet_stillBuildsTheNotification_withoutAPullDown() {
		// The monitoring service builds its first notification this way when no battery reading has arrived, so deciding on the pull-down must not need one.
		final Notification built = NotificationService.buildOngoingNotification(context, null);
		assertEquals("", String.valueOf(built.extras.getCharSequence(Notification.EXTRA_TEXT)));
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
	public void icon_saysUnknown_whenSnapshotUnavailable() {
		// No reading at all must not render as a confident full battery.
		assertEquals(R.drawable.ic_stat_battery_unknown, OngoingStatusContent.ongoingIconRes(null));
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
