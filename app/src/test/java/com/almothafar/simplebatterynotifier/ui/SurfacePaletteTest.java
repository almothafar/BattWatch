package com.almothafar.simplebatterynotifier.ui;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.ThemeAttributes;
import com.almothafar.simplebatterynotifier.WcagContrast;

import com.google.android.material.R.attr;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The neutral ramp Material builds containers, dividers and inverted surfaces from (#340).
 * <p>
 * The app names eleven M3 colour roles and Material generates around twenty-six, so everything unnamed came from the baseline palette — whose neutrals are
 * tinted toward Material's own violet seed. Measured in OKLCH they sat at hue 312 while this app's primary sits at 222, about ninety degrees across. No one
 * surface looked wrong on its own; together they read as faintly purple against the teal and blue.
 * <p>
 * Asserted on <em>provenance</em> first, because contrast cannot see this defect: the baseline palette is perfectly legible, so a ratio check stays green
 * while every grey comes from the wrong place. That is the lesson #333 paid for. The ratios are still asserted, guarding the other direction — a future
 * palette edit that lands somewhere unreadable.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SurfacePaletteTest {

	/** Every neutral role the app states, paired with the resource it must come from. */
	private static final int[][] RAMP = {
		{attr.colorSurfaceContainerLowest, R.color.md_theme_surfaceContainerLowest},
		{attr.colorSurfaceContainerLow, R.color.md_theme_surfaceContainerLow},
		{attr.colorSurfaceContainer, R.color.md_theme_surfaceContainer},
		{attr.colorSurfaceContainerHigh, R.color.md_theme_surfaceContainerHigh},
		{attr.colorSurfaceContainerHighest, R.color.md_theme_surfaceContainerHighest},
		{attr.colorSurfaceVariant, R.color.md_theme_surfaceVariant},
		{attr.colorOnSurfaceVariant, R.color.md_theme_onSurfaceVariant},
		{attr.colorSurfaceInverse, R.color.md_theme_surfaceInverse},
		{attr.colorOnSurfaceInverse, R.color.md_theme_onSurfaceInverse},
		{attr.colorOutline, R.color.md_theme_outline},
		{attr.colorOutlineVariant, R.color.md_theme_outlineVariant},
	};

	/** The five container steps, lightest-named first; Material reads elevation off their order. */
	private static final int[] STEPS = {
		attr.colorSurfaceContainerLowest,
		attr.colorSurfaceContainerLow,
		attr.colorSurfaceContainer,
		attr.colorSurfaceContainerHigh,
		attr.colorSurfaceContainerHighest,
	};

	private static void assertTheRampIsTheApps(String mode) {
		final Context themed = ThemeAttributes.appTheme();

		for (final int[] role : RAMP) {
			assertEquals(mode + ": role is not coming from the app palette",
			             ContextCompat.getColor(themed, role[1]), ThemeAttributes.color(themed, role[0]));
		}
	}

	private static void assertTextStaysLegible(String mode) {
		final Context themed = ThemeAttributes.appTheme();
		final int onSurface = ThemeAttributes.color(themed, attr.colorOnSurface);

		for (final int step : STEPS) {
			assertRatio(mode + ": body text on a container step", onSurface, ThemeAttributes.color(themed, step), WcagContrast.AA_NORMAL_TEXT);
		}
		assertRatio(mode + ": onSurfaceVariant on surfaceVariant",
		            ThemeAttributes.color(themed, attr.colorOnSurfaceVariant), ThemeAttributes.color(themed, attr.colorSurfaceVariant),
		            WcagContrast.AA_NORMAL_TEXT);
		assertRatio(mode + ": onSurfaceInverse on surfaceInverse",
		            ThemeAttributes.color(themed, attr.colorOnSurfaceInverse), ThemeAttributes.color(themed, attr.colorSurfaceInverse),
		            WcagContrast.AA_NORMAL_TEXT);
		assertRatio(mode + ": outline on the dialog panel",
		            ThemeAttributes.color(themed, attr.colorOutline), ThemeAttributes.color(themed, attr.colorSurfaceContainerHigh),
		            WcagContrast.AA_LARGE_TEXT);
	}

	private static void assertRatio(String what, int foreground, int background, double floor) {
		final double ratio = WcagContrast.ratio(foreground, background);

		assertTrue(String.format(Locale.ROOT, "%s: #%06X on #%06X is %.2f:1, below %.1f:1",
		                         what, foreground & 0xFFFFFF, background & 0xFFFFFF, ratio, floor),
		           ratio >= floor);
	}

	/**
	 * Elevation in Material is read off lightness, so the five steps have to stay ordered — lightest to darkest in light, the reverse in dark. A re-hue that
	 * kept every colour legible but shuffled the ramp would leave a raised surface sitting darker than the one it floats above.
	 */
	private static void assertElevationStillReads(String mode, boolean lightThemeDescends) {
		final Context themed = ThemeAttributes.appTheme();

		for (int i = 1; i < STEPS.length; i++) {
			final double previous = WcagContrast.luminance(ThemeAttributes.color(themed, STEPS[i - 1]));
			final double current = WcagContrast.luminance(ThemeAttributes.color(themed, STEPS[i]));

			assertTrue(mode + ": container step " + i + " breaks the elevation order",
			           lightThemeDescends ? current < previous : current > previous);
		}
	}

	/**
	 * Which way the greys lean, which is the defect itself rather than a proxy for it. Provenance above pins that the theme points at the app's resources;
	 * it cannot pin what those resources hold, so editing one back to a violet passes it.
	 * <p>
	 * Green against red separates the two casts without needing a colour space: a violet neutral is redder than it is green ({@code #ECE6F0} is 236/230),
	 * and a cyan-leaning one is the reverse ({@code #DFEBF0} is 223/235). Every M3 baseline value in this family fails this, and every replacement passes.
	 */
	private static void assertNoNeutralLeansViolet(String mode) {
		final Context themed = ThemeAttributes.appTheme();

		for (final int[] role : RAMP) {
			final int color = ThemeAttributes.color(themed, role[0]);

			assertTrue(String.format(Locale.ROOT, "%s: #%06X leans violet — red %d is above green %d",
			                         mode, color & 0xFFFFFF, Color.red(color), Color.green(color)),
			           Color.green(color) >= Color.red(color));
		}
	}

	@Test
	@Config(qualifiers = "notnight")
	public void noLightNeutralLeansViolet() {
		assertNoNeutralLeansViolet("light");
	}

	@Test
	@Config(qualifiers = "night")
	public void noDarkNeutralLeansViolet() {
		assertNoNeutralLeansViolet("dark");
	}

	@Test
	@Config(qualifiers = "notnight")
	public void theLightRampIsTheAppsOwn() {
		assertTheRampIsTheApps("light");
	}

	@Test
	@Config(qualifiers = "night")
	public void theDarkRampIsTheAppsOwn() {
		assertTheRampIsTheApps("dark");
	}

	@Test
	@Config(qualifiers = "notnight")
	public void lightTextStaysLegibleOnEveryNeutral() {
		assertTextStaysLegible("light");
	}

	@Test
	@Config(qualifiers = "night")
	public void darkTextStaysLegibleOnEveryNeutral() {
		assertTextStaysLegible("dark");
	}

	@Test
	@Config(qualifiers = "notnight")
	public void lightContainersGetDarkerAsTheyRise() {
		assertElevationStillReads("light", true);
	}

	@Test
	@Config(qualifiers = "night")
	public void darkContainersGetLighterAsTheyRise() {
		assertElevationStillReads("dark", false);
	}
}
