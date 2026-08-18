package com.almothafar.simplebatterynotifier.util;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.BidiVisualOrder;
import com.almothafar.simplebatterynotifier.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link BidiText#isolate}, the shared helper promoted out of {@code OngoingStatusContent} in #275.
 * <p>
 * Two properties, and the second is the one worth having. That the helper is a no-op in LTR locales is what lets every other test in this suite keep asserting
 * on plain strings. That an isolated quantity survives right-to-left layout is the defect itself — and it is checked by laying the text out
 * ({@link BidiVisualOrder}), because the characters are identical either way and only their order differs.
 */
@RunWith(Enclosed.class)
public class BidiTextTest {

	/** The Arabic warning alert, minus the level: "Battery at %1$s — keep an eye on your usage". */
	private static final String ARABIC_PROSE = "البطارية عند %1$s — انتبه لاستهلاكك";

	/**
	 * Under English the helper must change nothing at all. Every other test in this suite runs in the default locale and asserts on plain strings, so a helper
	 * that quietly inserted marks here would leave invisible characters in English copy and break those assertions in a way that reads as unrelated.
	 */
	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34)
	public static class UnderAnLtrLocale {

		@Test
		public void isolate_returnsTheValueUntouched() {
			assertEquals("32.0 °C", BidiText.isolate("32.0 °C"));
			assertEquals("20%", BidiText.isolate("20%"));
		}

		@Test
		public void isolate_passesNullThrough() {
			assertNull(BidiText.isolate(null));
		}
	}

	/**
	 * Under a region-bearing Arabic locale — {@code ar-rEG}, what Android's language picker actually produces, and the locale whose Western digits (#273) are
	 * what put this copy in the bidi algorithm's path in the first place.
	 */
	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public static class UnderAnRtlLocale {

		@Before
		public void assertTheQualifierApplied() {
			// Premise, in @Before rather than a @Test of its own: JUnit gives no ordering between test methods, so as a sibling test this could only report the
			// bad locale after the others had already failed on it with a confusing bidi message. Every test below is meaningless if the qualifier slipped.
			assertEquals("ar-rEG should be the default locale", "ar", Locale.getDefault().getLanguage());
		}

		@Test
		public void isolate_marksTheValueWithoutAddingAnythingVisible() {
			final String isolated = BidiText.isolate("20%");

			assertTrue("nothing was added, so the layout direction was not detected as RTL: " + isolated, isolated.length() > "20%".length());
			// The additions are invisible: what a reader sees is still exactly the value.
			assertEquals("20%", BidiVisualOrder.inRtlParagraph(isolated));
		}

		@Test
		public void isolate_keepsAPercentSignOnItsNumberInsideArabicProse() {
			// Premise: this is the defect. Without isolation the '%' is a neutral between Arabic prose and a number, and lands on the number's far side.
			assertTrue(BidiVisualOrder.inRtlParagraph(String.format(ARABIC_PROSE, "20%")).contains("%20"));

			BidiVisualOrder.assertRendersAsWritten(String.format(ARABIC_PROSE, BidiText.isolate("20%")), "20%");
		}

		@Test
		public void isolate_keepsAUnitOnItsNumberInsideArabicProse() {
			final Context context = ApplicationProvider.getApplicationContext();
			// The formatted temperature, straight from the production formatter: a number, a neutral degree sign and a Latin unit letter.
			final String temperature = TemperatureUtils.format(context, 320);

			BidiVisualOrder.assertRendersAsWritten(context.getString(R.string.notification_temperature_content, BidiText.isolate(temperature)), temperature);
		}

		/**
		 * Isolating the digits and leaving the sign behind in the resource fixes nothing — the property that decides <em>what</em> a caller has to pass, and the
		 * reason {@code notification_fast_drain_content} had to give up its {@code %%} in favour of an argument that already carries one.
		 */
		@Test
		public void isolate_doesNotReachASignLeftOutsideTheRun() {
			final String signOutside = "البطارية عند " + BidiText.isolate("20") + "% — انتبه";

			assertTrue("a '%' outside the isolated run should still reorder: " + signOutside,
					BidiVisualOrder.inRtlParagraph(signOutside).contains("%20"));
		}
	}
}
