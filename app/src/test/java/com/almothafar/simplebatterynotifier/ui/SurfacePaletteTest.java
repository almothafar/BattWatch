package com.almothafar.simplebatterynotifier.ui;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.almothafar.simplebatterynotifier.AppPalette;
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

	/** One neutral role: the Material attribute a widget resolves, and the app resource it has to come from. */
	private record NeutralRole(int attr, int color) { }

	/** Which way the container steps travel, since elevation is read off lightness and the two themes run opposite ways. */
	private enum Mode {
		LIGHT(true),
		DARK(false);

		private final boolean containersDarkenAsTheyRise;

		Mode(boolean containersDarkenAsTheyRise) {
			this.containersDarkenAsTheyRise = containersDarkenAsTheyRise;
		}

		private String label() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Every neutral role the app states, paired with the resource it must come from. */
	private static final NeutralRole[] RAMP = {
		new NeutralRole(attr.colorSurfaceContainerLowest, R.color.md_theme_surfaceContainerLowest),
		new NeutralRole(attr.colorSurfaceContainerLow, R.color.md_theme_surfaceContainerLow),
		new NeutralRole(attr.colorSurfaceContainer, R.color.md_theme_surfaceContainer),
		new NeutralRole(attr.colorSurfaceContainerHigh, R.color.md_theme_surfaceContainerHigh),
		new NeutralRole(attr.colorSurfaceContainerHighest, R.color.md_theme_surfaceContainerHighest),
		new NeutralRole(attr.colorSurfaceVariant, R.color.md_theme_surfaceVariant),
		new NeutralRole(attr.colorOnSurfaceVariant, R.color.md_theme_onSurfaceVariant),
		new NeutralRole(attr.colorSurfaceInverse, R.color.md_theme_surfaceInverse),
		new NeutralRole(attr.colorOnSurfaceInverse, R.color.md_theme_onSurfaceInverse),
		new NeutralRole(attr.colorOutline, R.color.md_theme_outline),
		new NeutralRole(attr.colorOutlineVariant, R.color.md_theme_outlineVariant),
	};

	/** The five container steps, lightest-named first; Material reads elevation off their order. */
	private static final int[] STEPS = {
		attr.colorSurfaceContainerLowest,
		attr.colorSurfaceContainerLow,
		attr.colorSurfaceContainer,
		attr.colorSurfaceContainerHigh,
		attr.colorSurfaceContainerHighest,
	};

	private static void assertTheRampIsTheApps(Mode mode) {
		final Context themed = ThemeAttributes.appTheme();

		for (final NeutralRole role : RAMP) {
			assertEquals(mode.label() + ": role is not coming from the app palette", ContextCompat.getColor(themed, role.color()),
			             ThemeAttributes.color(themed, role.attr()));
		}
	}

	private static void assertTextStaysLegible(Mode mode) {
		final Context themed = ThemeAttributes.appTheme();
		final int onSurface = ThemeAttributes.color(themed, attr.colorOnSurface);

		for (final int step : STEPS) {
			WcagContrast.assertRatioAtLeast(mode.label() + ": body text on a container step", onSurface, ThemeAttributes.color(themed, step),
			                                WcagContrast.AA_NORMAL_TEXT);
		}
		WcagContrast.assertRatioAtLeast(mode.label() + ": onSurfaceVariant on surfaceVariant",
		                                ThemeAttributes.color(themed, attr.colorOnSurfaceVariant),
		                                ThemeAttributes.color(themed, attr.colorSurfaceVariant), WcagContrast.AA_NORMAL_TEXT);
		WcagContrast.assertRatioAtLeast(mode.label() + ": onSurfaceInverse on surfaceInverse",
		                                ThemeAttributes.color(themed, attr.colorOnSurfaceInverse),
		                                ThemeAttributes.color(themed, attr.colorSurfaceInverse), WcagContrast.AA_NORMAL_TEXT);
		WcagContrast.assertRatioAtLeast(mode.label() + ": outline on the dialog panel", ThemeAttributes.color(themed, attr.colorOutline),
		                                ThemeAttributes.color(themed, attr.colorSurfaceContainerHigh), WcagContrast.AA_LARGE_TEXT);
	}

	/**
	 * Elevation in Material is read off lightness, so the five steps have to stay ordered — lightest to darkest in light, the reverse in dark. A re-hue that
	 * kept every colour legible but shuffled the ramp would leave a raised surface sitting darker than the one it floats above.
	 */
	private static void assertElevationStillReads(Mode mode) {
		final Context themed = ThemeAttributes.appTheme();

		for (int i = 1; i < STEPS.length; i++) {
			final double previous = WcagContrast.luminance(ThemeAttributes.color(themed, STEPS[i - 1]));
			final double current = WcagContrast.luminance(ThemeAttributes.color(themed, STEPS[i]));

			assertTrue(mode.label() + ": container step " + i + " breaks the elevation order",
			           mode.containersDarkenAsTheyRise ? current < previous : current > previous);
		}
	}

	/**
	 * Which way the greys lean, which is the defect itself rather than a proxy for it. Provenance above pins that the theme points at the app's resources;
	 * it cannot pin what those resources hold, so editing one back to a violet passes it.
	 * <p>
	 * Green against red separates the two casts without needing a colour space: a violet neutral is redder than it is green ({@code #ECE6F0} is 236/230),
	 * and a cyan-leaning one is the reverse ({@code #DFEBF0} is 223/235).
	 * <p>
	 * One role is beyond both guards in light, and it is worth naming rather than leaving to be discovered: {@code surfaceContainerLowest} is {@code #FFFFFF}
	 * here <em>and</em> in Material's baseline. Provenance compares the theme against the resource, which stay equal either way, and pure white is not redder
	 * than it is green — so dropping that one theme item passes every light assertion. Only the dark test catches it, where the two values differ.
	 */
	private static void assertNoNeutralLeansViolet(Mode mode) {
		final Context themed = ThemeAttributes.appTheme();

		for (final NeutralRole role : RAMP) {
			AppPalette.assertLeansWithTheBrand(mode.label() + ": neutral role", ThemeAttributes.color(themed, role.attr()));
		}
	}

	@Test
	@Config(qualifiers = "notnight")
	public void noLightNeutralLeansViolet() {
		assertNoNeutralLeansViolet(Mode.LIGHT);
	}

	@Test
	@Config(qualifiers = "night")
	public void noDarkNeutralLeansViolet() {
		assertNoNeutralLeansViolet(Mode.DARK);
	}

	@Test
	@Config(qualifiers = "notnight")
	public void theLightRampIsTheAppsOwn() {
		assertTheRampIsTheApps(Mode.LIGHT);
	}

	@Test
	@Config(qualifiers = "night")
	public void theDarkRampIsTheAppsOwn() {
		assertTheRampIsTheApps(Mode.DARK);
	}

	@Test
	@Config(qualifiers = "notnight")
	public void lightTextStaysLegibleOnEveryNeutral() {
		assertTextStaysLegible(Mode.LIGHT);
	}

	@Test
	@Config(qualifiers = "night")
	public void darkTextStaysLegibleOnEveryNeutral() {
		assertTextStaysLegible(Mode.DARK);
	}

	@Test
	@Config(qualifiers = "notnight")
	public void lightContainersGetDarkerAsTheyRise() {
		assertElevationStillReads(Mode.LIGHT);
	}

	@Test
	@Config(qualifiers = "night")
	public void darkContainersGetLighterAsTheyRise() {
		assertElevationStillReads(Mode.DARK);
	}
}
