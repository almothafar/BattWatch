package com.almothafar.simplebatterynotifier.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the home gauge's accessibility announcement, which is the one string the gauge produces in words rather than in paint.
 * <p>
 * It reached the same Eastern-digit defect as the alerts (#273) by a different route: not {@code getString} formatting with the configuration locale, but an
 * explicit {@code String.format(Locale.getDefault(), …)}. TalkBack reading {@code ٤٠} while every other surface in the app reads {@code 40} is the regression
 * these pin.
 * <p>
 * Two construction paths, because the gauge has two sources for the template and they have to agree on placeholder type. The layout applies
 * {@code Widget.App.BatteryGauge}, which supplies {@code battery_progress_description}; without a style the hardcoded fallback is used instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HorseshoeProgressBarTest {

	/** The gauge as {@code activity_main} builds it — style attached, so the template is the real string resource. */
	private static HorseshoeProgressBar styledGaugeAt(int level) {
		final AttributeSet attrs = Robolectric.buildAttributeSet()
		                                      .setStyleAttribute("@style/Widget.App.BatteryGauge")
		                                      .build();
		final HorseshoeProgressBar gauge = new HorseshoeProgressBar(context(), attrs);
		gauge.setLevel(level);
		return gauge;
	}

	/** The gauge with no style, so the template is the hardcoded fallback. */
	private static HorseshoeProgressBar bareGaugeAt(int level) {
		final HorseshoeProgressBar gauge = new HorseshoeProgressBar(context());
		gauge.setLevel(level);
		return gauge;
	}

	private static Context context() {
		return ApplicationProvider.getApplicationContext();
	}

	@Test
	public void contentDescription_namesTheLevel() {
		final CharSequence description = styledGaugeAt(40).getContentDescription();

		assertNotNull("the gauge should describe itself for TalkBack", description);
		assertTrue(description.toString(), description.toString().contains("40"));
	}

	/**
	 * The announcement keeps Western digits on a region-bearing Arabic locale (#96/#273).
	 * <p>
	 * The region tag carries the test: under a bare {@code "ar"} qualifier CLDR selects the Western numbering system anyway, so an assertion on {@code "40"}
	 * would pass against the defect as readily as against the fix. The premise is asserted first so the test can visibly still fail.
	 */
	@Test
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public void contentDescription_keepsWesternDigitsUnderARegionBearingArabicLocale() {
		// Premise: this locale really does localise digits, so the assertions below are not vacuous.
		assertEquals("ar-rEG should select Eastern Arabic digits", "٤٠", String.format(Locale.getDefault(), "%d", 40));

		final String description = String.valueOf(styledGaugeAt(40).getContentDescription());

		// values-ar was actually loaded, so this is the Arabic announcement and not an English fallback.
		assertTrue(description, description.contains("البطارية"));
		assertTrue(description, description.contains("40"));
		assertTrue("Eastern digits leaked into the gauge announcement: " + description, !description.contains("٤٠"));
	}

	/**
	 * The hardcoded fallback has to agree with the resource on placeholder type: the level arrives already formatted, so a {@code %1$d} there would not merely
	 * localise the digits, it would throw {@link java.util.IllegalFormatConversionException} and take the gauge down with it.
	 */
	@Test
	public void contentDescription_survivesTheFallbackFormat() {
		final String description = String.valueOf(bareGaugeAt(72).getContentDescription());

		assertTrue(description, description.contains("72"));
	}
}
