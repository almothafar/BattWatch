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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the full-battery alert's copy (#263), which is the one place the charge target reaches the
 * user in words.
 * <p>
 * Two things need pinning. Which of the two string sets is chosen — "you can unplug now" must never
 * claim a target the battery didn't reach, and must never call a charge complete when it isn't — and
 * that the parameterised strings actually format, in <b>both</b> locales: the previous full-battery copy
 * took no arguments at all, so a stray {@code %} in either translation would first surface as an
 * {@code IllegalFormatException} on a user's phone at the end of a charge.
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
		// Plugged in at 95% with a target of 90: "reached your 90% target" is true, so the almost-full
		// copy stands.
		setChargeTarget(90);

		final NotificationConfig config = fullAlertAt(95);

		assertEquals(context.getString(R.string.notification_almost_full_title), config.title);
		assertTrue(config.content.contains("90"));
	}

	@Test
	public void theTickerReportsTheLevelReachedNotTheTarget() {
		// The two numbers are deliberately different: 95% is where the battery got to, 90% is the target
		// it crossed. Interpolating the target into both would claim the level was 90.
		setChargeTarget(90);

		final NotificationConfig config = fullAlertAt(95);

		assertTrue(config.ticker, config.ticker.contains("95%"));
		assertTrue(config.ticker, !config.ticker.contains("90%"));
		assertTrue(config.content, config.content.contains("90%"));
	}

	@Test
	public void chargeCompletedBelowTheTarget_reportsACompletedChargeNotTheTarget() {
		// A charge-capped device (Samsung "Protect battery" at 85%) reports the charge finished short of
		// the target. Saying "reached your 90% charge target" at 85% would state something that did not
		// happen — the honest report is the one the battery gave: this charge is done.
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
		assertTrue(config.content, !config.content.contains("%1$d"));
		assertTrue(config.bigContent, !config.bigContent.contains("%1$d"));
	}

	@Test
	@Config(sdk = 34, qualifiers = "ar")
	public void almostFullCopy_formatsInArabic() {
		// The Arabic strings carry both a positional argument and literal percent signs, which have to be
		// escaped independently of the English ones; getString would throw on a mismatch.
		setChargeTarget(85);

		final NotificationConfig config = fullAlertAt(85);

		// "charge target" in Arabic — proves values-ar was actually loaded rather than falling back to
		// English, which would make the rest of these assertions vacuous.
		assertTrue(config.content, config.content.contains("هدف الشحن"));
		assertTrue(config.content, config.content.contains("85"));
		assertTrue(config.bigContent, config.bigContent.contains("85"));
		assertTrue(config.bigContent, config.bigContent.contains("100"));
		assertTrue(config.content, !config.content.contains("%1$d"));
		assertTrue(config.bigContent, !config.bigContent.contains("%1$d"));
	}

	@Test
	@Config(sdk = 34, qualifiers = "ar")
	public void chargeCompleteCopy_formatsInArabic() {
		setChargeTarget(AppPrefs.MAX_CHARGE_TARGET);

		final NotificationConfig config = fullAlertAt(100);

		assertEquals(context.getString(R.string.notification_full_level_title), config.title);
		assertTrue(config.bigContent, config.bigContent.contains("100"));
	}
}
