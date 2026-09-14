package com.almothafar.simplebatterynotifier;

import android.graphics.Color;

import java.util.Locale;

import static org.junit.Assert.assertTrue;

/**
 * Whether a colour belongs to this app's palette or to Material's.
 * <p>
 * BattWatch is built on a cyan-blue primary — {@code #059bbf}, hue 222 in OKLCH — while Material's baseline palette is generated from a violet seed and sits
 * near hue 312. Every role the theme leaves unstated falls back to that baseline, which is how the lavender snackbar action (#333) and the whole violet
 * neutral ramp (#340) reached the app unnoticed.
 * <p>
 * Green against red separates the two casts without needing a colour space, and it is enough because the question is only which side of neutral a colour sits
 * on: a violet is redder than it is green ({@code #D0BCFF} is 208/188, {@code #ECE6F0} is 236/230), and anything drawn from this palette is the reverse
 * ({@code #6ED3EC} is 110/211, {@code #DFEBF0} is 223/235).
 * <p>
 * Worth being clear about why this is needed at all, because the obvious guards both miss it. Asserting that a role resolves to the app's own resource pins
 * the <em>wiring</em> and not the <em>value</em> — edit the resource to a lavender and both sides of that comparison move together. And contrast is blind by
 * construction: Material's baseline is perfectly legible, which is exactly why nobody noticed it for so long.
 */
public final class AppPalette {

	private AppPalette() {
		// Utility class - prevent instantiation
	}

	/**
	 * Assert a colour leans with this app's palette rather than toward Material's violet baseline.
	 *
	 * @param what  what the colour is, for the failure message
	 * @param color the colour to check, as an ARGB int
	 */
	public static void assertLeansWithTheBrand(String what, int color) {
		assertTrue(String.format(Locale.ROOT, "%s: #%06X leans violet — red %d is above green %d",
		                         what, color & 0xFFFFFF, Color.red(color), Color.green(color)),
		           Color.green(color) >= Color.red(color));
	}
}
