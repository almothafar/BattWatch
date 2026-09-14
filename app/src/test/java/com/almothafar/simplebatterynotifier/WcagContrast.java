package com.almothafar.simplebatterynotifier;

import android.graphics.Color;

import java.util.Locale;

import static org.junit.Assert.assertTrue;

/**
 * Relative luminance and contrast ratio, per WCAG 2.1.
 * <p>
 * Shared because two suites measure the same thing on different parts of the palette: {@code SnackbarActionContrastTest} on the action Material paints over
 * an inverted surface (#333), and {@code SurfacePaletteTest} across the neutral ramp (#340).
 * <p>
 * Worth remembering what a passing ratio does and does not prove. Material's own baseline palette is perfectly legible, so a contrast assertion stays green
 * while a colour comes from entirely the wrong place — which is how the stock lavender went unnoticed until #333. Pair these with an assertion that the
 * colour is the app's own; neither question implies the other.
 */
public final class WcagContrast {

	/** WCAG AA for normal-size text. */
	public static final double AA_NORMAL_TEXT = 4.5d;

	/** WCAG AA for large text, and for the boundary of a user-interface component such as an outline. */
	public static final double AA_LARGE_TEXT = 3.0d;

	private WcagContrast() {
		// Utility class - prevent instantiation
	}

	/**
	 * The contrast ratio between two colours, from 1:1 (identical) to 21:1 (black on white).
	 *
	 * @param foreground the colour drawn on top, as an ARGB int
	 * @param background the colour behind it, as an ARGB int
	 *
	 * @return the ratio, expressed as the number before the ":1"
	 */
	public static double ratio(int foreground, int background) {
		final double a = luminance(foreground);
		final double b = luminance(background);

		return (Math.max(a, b) + 0.05d) / (Math.min(a, b) + 0.05d);
	}

	/**
	 * Relative luminance, per WCAG 2.1.
	 *
	 * @param color an ARGB int
	 *
	 * @return luminance from 0 (black) to 1 (white)
	 */
	public static double luminance(int color) {
		return 0.2126d * channel(Color.red(color)) + 0.7152d * channel(Color.green(color)) + 0.0722d * channel(Color.blue(color));
	}

	private static double channel(int eightBit) {
		final double c = eightBit / 255d;

		return c <= 0.03928d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
	}

	/**
	 * Assert a pair clears its contrast floor, naming both colours and the ratio when it does not — a bare "expected true" tells whoever broke it nothing
	 * about which colour moved or how far.
	 *
	 * @param what       what the pair is, for the failure message
	 * @param foreground the colour drawn on top, as an ARGB int
	 * @param background the colour behind it, as an ARGB int
	 * @param floor      the minimum acceptable ratio, normally {@link #AA_NORMAL_TEXT} or {@link #AA_LARGE_TEXT}
	 */
	public static void assertRatioAtLeast(String what, int foreground, int background, double floor) {
		final double ratio = ratio(foreground, background);

		assertTrue(String.format(Locale.ROOT, "%s: #%06X on #%06X is %.2f:1, below %.1f:1",
		                         what, foreground & 0xFFFFFF, background & 0xFFFFFF, ratio, floor),
		           ratio >= floor);
	}
}
