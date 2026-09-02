package com.almothafar.simplebatterynotifier.ui;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.ThemeAttributes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The snackbar action's legibility (#333). Material paints a snackbar on an <em>inverted</em> surface and colours its action from
 * {@code colorPrimaryInverse}, so both ends of that pair come from a corner of the palette nothing else in the app touches — which is exactly how it went
 * unnoticed that neither was defined, leaving M3's lavender {@code #6750A4} on the theme snackbar.
 * <p>
 * Two assertions, because they catch different failures and neither implies the other. That the colour is the app's at all is the one that matters here:
 * M3's lavender is perfectly legible - 7.7:1 in light, 5.0:1 in dark - so a contrast check alone passes happily while the snackbar goes purple, which is
 * exactly what mutating this theme showed. The ratio guards the other direction: the obvious brand colour is the wrong one in each mode, since the light
 * theme's snackbar is a dark slab and the dark theme's is a light one, and the app's mid blue #059bbf manages only 2.5:1 against the latter.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SnackbarActionContrastTest {

	/** WCAG AA for normal-size text. The action label is not large text. */
	private static final double AA_NORMAL_TEXT = 4.5d;

	/** Relative luminance, per WCAG 2.1. */
	private static double luminance(int color) {
		return 0.2126d * channel(Color.red(color)) + 0.7152d * channel(Color.green(color)) + 0.0722d * channel(Color.blue(color));
	}

	private static double channel(int eightBit) {
		final double c = eightBit / 255d;
		return c <= 0.03928d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
	}

	private static double contrast(int foreground, int background) {
		final double a = luminance(foreground);
		final double b = luminance(background);
		return (Math.max(a, b) + 0.05d) / (Math.min(a, b) + 0.05d);
	}

	private static void assertTheActionIsLegible(String mode) {
		final Context themed = ThemeAttributes.appTheme();
		final int action = ThemeAttributes.color(themed, com.google.android.material.R.attr.colorPrimaryInverse);
		final int slab = ThemeAttributes.color(themed, com.google.android.material.R.attr.colorSurfaceInverse);
		final double ratio = contrast(action, slab);

		assertEquals(mode + ": the action is not coming from the app palette", ContextCompat.getColor(themed, R.color.md_theme_primaryInverse), action);

		assertTrue(String.format(Locale.ROOT, "%s: snackbar action #%06X on #%06X is %.2f:1, below %.1f:1",
		                         mode, action & 0xFFFFFF, slab & 0xFFFFFF, ratio, AA_NORMAL_TEXT),
		           ratio >= AA_NORMAL_TEXT);
	}

	@Test
	@Config(qualifiers = "notnight")
	public void theActionIsLegibleOnTheLightThemesDarkSnackbar() {
		assertTheActionIsLegible("light");
	}

	@Test
	@Config(qualifiers = "night")
	public void theActionIsLegibleOnTheDarkThemesLightSnackbar() {
		assertTheActionIsLegible("dark");
	}
}
