package com.almothafar.simplebatterynotifier.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.util.AppPrefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the full-battery alert's copy (#263), which is the one place the charge target reaches the user in words.
 * <p>
 * Two things need pinning. Which of the two string sets is chosen — "you can unplug now" must never claim a target the battery didn't reach, and must never
 * call a charge complete when it isn't — and that the parameterised strings actually format, in <b>both</b> locales: the previous full-battery copy took no
 * arguments at all, so a stray {@code %} in either translation would first surface as an {@code IllegalFormatException} on a user's phone at the end of a
 * charge.
 * <p>
 * The digits those numbers render in are pinned here too (#273): every level alert names a percentage, and {@code getString} formats with the configuration
 * locale, so the copy is the surface where a Eastern-digit regression would actually reach a user.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NotificationConfigTest {

	private Context context;
	private SharedPreferences prefs;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	private void setChargeTarget(int target) {
		prefs.edit().putInt(context.getString(R.string._pref_key_charge_target), target).commit();
	}

	private void setLevels(int critical, int warning) {
		prefs.edit()
		     .putInt(context.getString(R.string._pref_key_critical_battery_level), critical)
		     .putInt(context.getString(R.string._pref_key_warn_battery_level), warning)
		     .commit();
	}

	private NotificationConfig fullAlertAt(int levelPercent) {
		return new NotificationConfig(context, prefs, AlertType.FULL, levelPercent);
	}

	@Test
	public void maximumTarget_keepsTheOriginalChargeCompleteCopy() {
		setChargeTarget(AppPrefs.MAX_CHARGE_TARGET);

		final NotificationConfig config = fullAlertAt(100);

		assertEquals(context.getString(R.string.notification_full_level_title), config.title);
		assertEquals(context.getString(R.string.notification_full_level_content), config.content);
		assertEquals(context.getString(R.string.notification_full_level_ticker), config.ticker);
		assertEquals(context.getString(R.string.notification_full_level_content_big), config.bigContent);
	}

	@Test
	public void reachingTheTarget_saysAlmostFullAndNamesTheTarget() {
		setChargeTarget(90);

		final NotificationConfig config = fullAlertAt(90);

		assertEquals(context.getString(R.string.notification_almost_full_title), config.title);
		assertTrue("the body should name the target: " + config.content, config.content.contains("90"));
		assertTrue("the expanded body should name the target: " + config.bigContent, config.bigContent.contains("90"));
	}

	@Test
	public void firingAboveTheTarget_stillNamesTheTargetItReached() {
		// Plugged in at 95% with a target of 90: "reached your 90% target" is true, so the almost-full copy stands.
		setChargeTarget(90);

		final NotificationConfig config = fullAlertAt(95);

		assertEquals(context.getString(R.string.notification_almost_full_title), config.title);
		assertTrue(config.content.contains("90"));
	}

	@Test
	public void theTickerReportsTheLevelReachedNotTheTarget() {
		// The two numbers are deliberately different: 95% is where the battery got to, 90% is the target it crossed. Interpolating the target into both would
		// claim the level was 90.
		setChargeTarget(90);

		final NotificationConfig config = fullAlertAt(95);

		assertTrue(config.ticker, config.ticker.contains("95%"));
		assertTrue(config.ticker, !config.ticker.contains("90%"));
		assertTrue(config.content, config.content.contains("90%"));
	}

	@Test
	public void chargeCompletedBelowTheTarget_reportsACompletedChargeNotTheTarget() {
		// A charge-capped device (Samsung "Protect battery" at 85%) reports the charge finished short of the target. Saying "reached your 90% charge target" at
		// 85% would state something that did not happen — the honest report is the one the battery gave: this charge is done.
		setChargeTarget(90);

		final NotificationConfig config = fullAlertAt(85);

		assertEquals(context.getString(R.string.notification_full_level_title), config.title);
		assertEquals(context.getString(R.string.notification_full_level_content), config.content);
	}

	@Test
	public void almostFullCopy_formatsInEnglish() {
		setChargeTarget(85);

		final NotificationConfig config = fullAlertAt(85);

		// No unformatted placeholders survive into what the user reads.
		assertTrue(config.content, config.content.contains("85%"));
		assertTrue(config.bigContent, config.bigContent.contains("85%"));
		assertTrue(config.bigContent, config.bigContent.contains("100%"));
		assertTrue(config.content, !config.content.contains("%1$s"));
		assertTrue(config.bigContent, !config.bigContent.contains("%1$s"));
	}

	@Test
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public void almostFullCopy_formatsInArabic() {
		// The Arabic strings carry both a positional argument and literal percent signs, which have to be escaped independently of the English ones; getString
		// would throw on a mismatch.
		setChargeTarget(85);

		final NotificationConfig config = fullAlertAt(85);

		// "charge target" in Arabic — proves values-ar was actually loaded rather than falling back to English, which would make the rest of these assertions
		// vacuous.
		assertTrue(config.content, config.content.contains("هدف الشحن"));
		assertTrue(config.content, config.content.contains("85%"));
		assertTrue(config.bigContent, config.bigContent.contains("85%"));
		assertTrue(config.bigContent, config.bigContent.contains("100%"));
		assertTrue(config.content, !config.content.contains("%1$s"));
		assertTrue(config.bigContent, !config.bigContent.contains("%1$s"));
	}

	/**
	 * The digits a real Arabic user sees stay Western (#96).
	 * <p>
	 * The region tag is the whole point of this test. Under a bare {@code "ar"} qualifier CLDR picks the Western numbering system anyway, so an assertion on
	 * {@code "85"} passes whether the code is right or wrong — that is precisely how #241 survived the #154 fix. {@code ar-rEG} is what Android's language
	 * picker actually produces, and there {@code getString} with a {@code %1$d} placeholder would render {@code ٨٥}. This asserts the premise first, so a
	 * future reader can see the test can still fail.
	 */
	@Test
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public void almostFullCopy_keepsWesternDigitsUnderARegionBearingArabicLocale() {
		// Premise: this locale really does localise digits, so the assertions below are not vacuous.
		assertEquals("ar-rEG should select Eastern Arabic digits", "٨٥", String.format(Locale.getDefault(), "%d", 85));

		setChargeTarget(85);

		final NotificationConfig config = fullAlertAt(85);

		assertTrue(config.ticker, config.ticker.contains("85%"));
		assertTrue(config.content, config.content.contains("85%"));
		assertTrue(config.bigContent, config.bigContent.contains("85%"));
		assertTrue("Eastern digits leaked into the ticker: " + config.ticker, !config.ticker.contains("٨٥"));
		assertTrue("Eastern digits leaked into the body: " + config.content, !config.content.contains("٨٥"));
	}

	/**
	 * The critical and warning alerts keep Western digits too (#273).
	 * <p>
	 * These two predate the charge target and carried {@code %1$d} until #273; the level they name is the one number in the whole alert, so localising it was
	 * the entire content of the defect. Same region-bearing locale and same premise assertion as the almost-full test above, for the same reason.
	 */
	@Test
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public void levelAlertCopy_keepsWesternDigitsUnderARegionBearingArabicLocale() {
		// Premise: this locale really does localise digits, so the assertions below are not vacuous.
		assertEquals("ar-rEG should select Eastern Arabic digits", "١٥", String.format(Locale.getDefault(), "%d", 15));
		// Deliberately not 20/40: those are AppPrefs' own fallbacks, so the copy would read the same whether the levels were loaded or silently defaulted.
		setLevels(15, 35);

		final NotificationConfig critical = new NotificationConfig(context, prefs, AlertType.CRITICAL, 10);
		final NotificationConfig warning = new NotificationConfig(context, prefs, AlertType.WARNING, 30);

		assertTrue(critical.ticker, critical.ticker.contains("15%"));
		assertTrue(critical.content, critical.content.contains("15%"));
		assertTrue(critical.bigContent, critical.bigContent.contains("15%"));
		assertTrue(warning.ticker, warning.ticker.contains("35%"));
		assertTrue(warning.content, warning.content.contains("35%"));
		assertTrue(warning.bigContent, warning.bigContent.contains("35%"));

		assertTrue("Eastern digits leaked into the critical body: " + critical.content, !critical.content.contains("١٥"));
		assertTrue("Eastern digits leaked into the warning body: " + warning.content, !warning.content.contains("٣٥"));

		// values-ar was actually loaded on both paths, so the assertions above are about the Arabic copy and not an English fallback.
		assertTrue(critical.content, critical.content.contains("البطارية"));
		assertTrue(warning.content, warning.content.contains("البطارية"));
	}

	@Test
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public void chargeCompleteCopy_formatsInArabic() {
		setChargeTarget(AppPrefs.MAX_CHARGE_TARGET);

		final NotificationConfig config = fullAlertAt(100);

		assertEquals(context.getString(R.string.notification_full_level_title), config.title);
		assertTrue(config.bigContent, config.bigContent.contains("100"));
	}
}
