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

import static org.junit.Assert.assertEquals;

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

	private static void assertTheActionIsLegible(String mode) {
		final Context themed = ThemeAttributes.appTheme();
		final int action = ThemeAttributes.color(themed, attr.colorPrimaryInverse);
		final int slab = ThemeAttributes.color(themed, attr.colorSurfaceInverse);

		assertEquals(mode + ": the action is not coming from the app palette", ContextCompat.getColor(themed, R.color.md_theme_primaryInverse), action);
		AppPalette.assertLeansWithTheBrand(mode + ": snackbar action", action);
		WcagContrast.assertRatioAtLeast(mode + ": snackbar action", action, slab, WcagContrast.AA_NORMAL_TEXT);
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
