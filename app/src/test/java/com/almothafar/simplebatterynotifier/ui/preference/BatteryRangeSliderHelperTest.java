package com.almothafar.simplebatterynotifier.ui.preference;

import android.content.Context;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.BidiVisualOrder;
import com.almothafar.simplebatterynotifier.model.LevelThresholds;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the pure (context-free) clamping logic in {@link BatteryRangeSliderHelper}, which
 * guarantees the critical/warning pair is always inside the slider's bounds and at least
 * {@link BatteryRangeSliderHelper#MIN_SEPARATION} apart — the invariant that replaces the old
 * cross-field validation.
 */
@RunWith(Enclosed.class)
public class BatteryRangeSliderHelperTest {

	private static final int FROM = BatteryRangeSliderHelper.LEVEL_FROM;
	private static final int TO = BatteryRangeSliderHelper.LEVEL_TO;
	private static final int SEP = BatteryRangeSliderHelper.MIN_SEPARATION;

	/**
	 * {@link BatteryRangeSliderHelper#clampPair(LevelThresholds, int, int, int)} maps representative
	 * (critical, warning) inputs — valid, out-of-range, inverted, and too-close — to the expected
	 * clamped pair.
	 */
	@RunWith(Parameterized.class)
	public static class ClampPair {

		@Parameter(0) public int critical;
		@Parameter(1) public int warning;
		@Parameter(2) public int expectedCritical;
		@Parameter(3) public int expectedWarning;

		@Parameters(name = "clampPair({0},{1}) = [{2},{3}]")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{
					{20, 40, 20, 40},   // already-valid defaults, untouched
					{10, 50, 10, 50},   // full range, untouched
					{5, 40, 10, 40},    // critical below floor -> raised to floor
					{20, 60, 20, 50},   // warning above ceiling -> lowered to ceiling
					{30, 30, 30, 35},   // equal -> warning pushed up to keep the gap
					{40, 20, 40, 45},   // inverted -> warning pushed above critical
					{48, 49, 45, 50},   // too close near the ceiling -> both shifted down
					{2, 4, 10, 15},     // both below floor
					{60, 70, 45, 50},   // both above ceiling
			});
		}

		@Test
		public void clampsToExpectedPair() {
			final LevelThresholds result = BatteryRangeSliderHelper.clampPair(
					new LevelThresholds(critical, warning), FROM, TO, SEP);
			assertEquals("critical", expectedCritical, result.critical());
			assertEquals("warning", expectedWarning, result.warning());
		}
	}

	/**
	 * The thumb label the user drags.
	 * <p>
	 * This used to run through {@code battery_level_percent} — {@code translatable="false"}, which reads like protection against the Eastern-digit defect and
	 * is not, since {@code getString} formats with the <b>configuration</b> locale rather than the locale the string was declared in. It was the one string in
	 * that class of defect (#273) the issue did not list. The resource is gone now: once {@code BatteryPercentFormatter} owned the {@code '%'} the string had
	 * no text left but its own placeholder. So there is no locale-sensitive step left on this path to pin here — the digit property lives in the formatter and
	 * is tested there. What is left worth pinning is the rounding, which is this method's own decision.
	 */
	public static class Label {

		@Test
		public void roundsToAWholePercentAndCarriesTheSign() {
			assertEquals("40%", BatteryRangeSliderHelper.labelFor(40f));
			assertEquals("the slider's step is 1, but the formatter still owns the rounding", "40%", BatteryRangeSliderHelper.labelFor(39.6f));
			assertEquals("20%", BatteryRangeSliderHelper.labelFor(20.4f));
		}
	}

	/**
	 * The captions under the slider, which the settings screen and the home screen now both render through this one method.
	 * <p>
	 * Two properties, and neither is visible from the resource: the caption strings read {@code "Critical %1$s"}, so the {@code '%'} the user sees comes from
	 * {@code BatteryPercentFormatter} and vanishes silently if a caller ever passes the raw int — which {@code %1$s} accepts without complaint — and the digits
	 * are Western only because the formatter uses {@code Locale.ROOT}, since {@code getString} would otherwise render {@code ٢٠} here under {@code ar-EG}.
	 * <p>
	 * The whole reason this method exists is that the two surfaces used to hold a copy each; a test on one copy is how #241 survived the #154 fix.
	 */
	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public static class Captions {

		@Test
		public void labelsEachThresholdWithItsOwnWesternPercentage() {
			// Premise: this locale really does localise digits, so the assertions below are not vacuous.
			assertEquals("ar-rEG should select Eastern Arabic digits", "٢٠", String.format(Locale.getDefault(), "%d", 20));

			final Context context = ApplicationProvider.getApplicationContext();
			final TextView critical = new TextView(context);
			final TextView warning = new TextView(context);

			BatteryRangeSliderHelper.applyCaptions(context, critical, warning, new LevelThresholds(20, 40));

			// Exact matches: the Arabic words prove values-ar was loaded rather than falling back to English, the '%' proves the formatter supplied the sign the
			// resource no longer carries, and the digits prove it supplied them in ROOT. Compared with the bidi controls stripped, because #275 wraps each
			// percentage in isolation marks that are part of the string and never part of what is drawn.
			assertEquals("حرج 20%", BidiVisualOrder.stripControls(critical.getText().toString()));
			assertEquals("تحذير 40%", BidiVisualOrder.stripControls(warning.getText().toString()));

			// And those marks do their job: laid out right-to-left, the '%' stays on its number instead of drifting to the far side of it as "حرج %20" (#275).
			BidiVisualOrder.assertRendersAsWritten(critical.getText().toString(), "20%");
			BidiVisualOrder.assertRendersAsWritten(warning.getText().toString(), "40%");
		}

		@Test
		public void toleratesALayoutThatOmitsTheCaptions() {
			// MainActivity passes findViewById results straight in, so a layout without the caption views hands this two nulls on every drag frame.
			BatteryRangeSliderHelper.applyCaptions(ApplicationProvider.getApplicationContext(), null, null, new LevelThresholds(20, 40));
		}
	}

	/**
	 * Whatever the input — including corrupted values far outside the range — the result must always
	 * be a usable pair the slider can accept without tripping its own validation.
	 */
	public static class Invariants {

		@Test
		public void everyInputYieldsAValidPair() {
			for (int critical = -20; critical <= 80; critical++) {
				for (int warning = -20; warning <= 80; warning++) {
					final LevelThresholds r = BatteryRangeSliderHelper.clampPair(
							new LevelThresholds(critical, warning), FROM, TO, SEP);
					final String at = "(" + critical + "," + warning + ")";
					assertTrue("critical in range at " + at, r.critical() >= FROM && r.critical() <= TO);
					assertTrue("warning in range at " + at, r.warning() >= FROM && r.warning() <= TO);
					assertTrue("separation kept at " + at, r.warning() - r.critical() >= SEP);
					assertTrue("critical below warning at " + at, r.critical() < r.warning());
				}
			}
		}
	}
}
