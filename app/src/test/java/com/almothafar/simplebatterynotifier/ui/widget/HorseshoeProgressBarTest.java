package com.almothafar.simplebatterynotifier.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;

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
 * Three construction paths, because the template has three sources and they do not all agree on placeholder type. The layout applies
 * {@code Widget.App.BatteryGauge}, which supplies {@code battery_progress_description}; without a style the hardcoded fallback is used; and
 * {@code gaugeLevelDescription} is a public styleable attribute, so any caller may set a template of their own — including the {@code %1$d} form that predates
 * the catalogue rule. Each is built through a real constructor, so narrowing the accepted placeholder fails these rather than slipping past them.
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

	/** The gauge as a caller setting {@code gaugeLevelDescription} itself builds it, so the template really goes through the view rather than past it. */
	private static HorseshoeProgressBar gaugeWithTemplate(String template, int level) {
		final AttributeSet attrs = Robolectric.buildAttributeSet()
		                                      .addAttribute(R.attr.gaugeLevelDescription, template)
		                                      .build();
		final HorseshoeProgressBar gauge = new HorseshoeProgressBar(context(), attrs);
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

	/** With no style attached there is no {@code gaugeLevelDescription} to read, and the hardcoded fallback template has to carry the announcement itself. */
	@Test
	public void contentDescription_fallsBackToTheBuiltInTemplateWithoutAStyle() {
		assertEquals("Level at 72 percent", String.valueOf(bareGaugeAt(72).getContentDescription()));
	}

	/**
	 * A caller's own {@code %1$d} template still formats, and still formats Western.
	 * <p>
	 * {@code gaugeLevelDescription} is a public styleable attribute, so its accepted templates are a contract with callers this class does not own. The level
	 * is therefore passed to {@code String.format} as an int and never pre-formatted: {@code Locale.ROOT} already holds the digits Western for {@code %d} and
	 * {@code %s} alike, so pre-formatting would buy nothing and would turn an older {@code %1$d} template from a cosmetic digit bug into an
	 * {@code IllegalFormatConversionException} thrown out of view construction.
	 * <p>
	 * The template is fed through a real constructor rather than to {@code String.format} here, which is what makes this a test of the gauge: if
	 * {@code announceLevel} ever narrows to {@code %s}, this fails inside {@code gaugeWithTemplate} rather than passing on a property of the JDK.
	 */
	@Test
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public void contentDescription_acceptsACallersPercentDTemplate() {
		// Premise: this locale really does localise digits, so the assertion below is not vacuous.
		assertEquals("ar-rEG should select Eastern Arabic digits", "٧٢", String.format(Locale.getDefault(), "%d", 72));

		assertEquals("Level at 72 percent", String.valueOf(gaugeWithTemplate("Level at %1$d percent", 72).getContentDescription()));
	}
}
