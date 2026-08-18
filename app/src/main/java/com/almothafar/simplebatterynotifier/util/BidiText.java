package com.almothafar.simplebatterynotifier.util;

import android.view.View;

import androidx.core.text.BidiFormatter;
import androidx.core.text.TextUtilsCompat;

import java.util.Locale;

/**
 * Keeps a Latin value readable where it is interpolated into right-to-left copy (#194/#275).
 * <p>
 * Every number a user sees is Western — {@code 85}, never {@code ٨٥}, in any locale (#96, see {@link BatteryPercentFormatter}). Western digits are bidi class
 * {@code EN}, so a number dropped into an Arabic sentence is a left-to-right island inside right-to-left text, and the <em>neutral</em> characters touching it
 * — {@code %}, {@code °}, {@code ~}, {@code ·}, brackets — belong to neither direction. The Unicode bidi algorithm resolves each of those to whatever surrounds
 * it, which in Arabic prose is right-to-left, and they end up on the far side of the number they were written next to: "Battery at 20%" renders {@code %20},
 * and "Battery is at 32.0 °C" renders {@code C° 32.0} — the unit torn off its number and reversed.
 * <p>
 * Isolating the value marks it as one left-to-right run, so its own sign or unit travels with it. That makes <em>what</em> gets isolated the whole point: the
 * thing the user reads as a single quantity, sign and unit included ({@code "20%"}, {@code "32.0 °C"}, {@code "20%/h"}) — never the bare digits with the sign
 * left behind in the surrounding resource, because a {@code %} outside the isolated run reorders exactly as it did before.
 * <p>
 * Before #273 the question could not arise: the digits on these surfaces were Eastern Arabic, which is bidi class {@code AN} and orders natively inside RTL
 * text. Making them Western is the right call — it is the app's oldest display rule — but it is what moves this copy into the case this class exists for.
 * <p>
 * A no-op in left-to-right locales, so English copy is untouched.
 */
public final class BidiText {

	private BidiText() {
		// Utility class - prevent instantiation
	}

	/**
	 * Wrap a Latin value as an isolated left-to-right run, so neither it nor the neutral characters around it can be reordered inside right-to-left copy.
	 * <p>
	 * Direction comes from {@link Locale#getDefault()} rather than from a {@code Context}: the wrapping has to agree with the direction the text will actually
	 * be laid out in, and the default locale is what {@code TextView} resolves its own layout direction from.
	 *
	 * @param value the value to isolate — the whole quantity, sign and unit included; null passes through
	 *
	 * @return the value, bidi-isolated under an RTL locale; returned unchanged under an LTR one
	 */
	public static String isolate(String value) {
		if (TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) != View.LAYOUT_DIRECTION_RTL) {
			return value;
		}
		// getInstance(true) rather than getInstance(): the no-argument form resolves the layout direction from the default locale all over again, which the
		// line above has just done. This path runs several times per battery tick, and the boolean overload hands back a shared instance for a known context.
		return BidiFormatter.getInstance(true).unicodeWrap(value);
	}
}
