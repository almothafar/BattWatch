package com.almothafar.simplebatterynotifier.ui.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.ui.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the one dialog style the app does not build itself (#335). Every dialog in {@code MainActivity}, {@code BatteryInsightsActivity} and
 * {@code AboutDialog} goes through {@code MaterialAlertDialogBuilder}, which sets its panel in code; the six behind a preference row — Theme,
 * App language, Temperature Unit, charge-notification style and the two quiet-hours time pickers — are built by androidx.preference with
 * appcompat's {@code AlertDialog.Builder}, which reads {@code alertDialogTheme} and nothing else.
 * <p>
 * Two ways to regress, so the panel colour is read off the drawable rather than trusted from an attribute. Drop {@code alertDialogTheme} and these
 * dialogs fall back to appcompat's {@code abc_dialog_material_background}, which is white in dark mode too; point it at Material's own
 * {@code ThemeOverlay.Material3.MaterialAlertDialog} instead and the M3 layout arrives with no background of its own, leaving that same white shape
 * showing. Both were confirmed by mutating this theme and watching the two colour tests below fail. Only stating the panel drawable satisfies both.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PreferenceDialogThemeTest {

	/** What appcompat hands these dialogs when {@code alertDialogTheme} goes unset: a white panel, in dark mode as much as in light. */
	private static final int APPCOMPAT_FALLBACK_PANEL = 0xFFFFFFFF;

	private static Context appTheme() {
		return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme);
	}

	private static int attr(Context context, int attrId) {
		final TypedValue value = new TypedValue();
		assertTrue("attribute unresolved", context.getTheme().resolveAttribute(attrId, value, true));
		return value.type == TypedValue.TYPE_REFERENCE || value.resourceId != 0 ? value.resourceId : value.data;
	}

	private static int attrColor(Context context, int attrId) {
		final TypedValue value = new TypedValue();
		assertTrue("attribute unresolved", context.getTheme().resolveAttribute(attrId, value, true));
		return value.data;
	}

	/** The context androidx.preference's dialogs are themed with: the activity theme, overlaid with whatever {@code alertDialogTheme} names. */
	private static Context dialogTheme() {
		final Context base = appTheme();
		return new ContextThemeWrapper(base, attr(base, androidx.appcompat.R.attr.alertDialogTheme));
	}

	/** The colour the dialog panel actually paints, read off the drawable rather than the attribute it was declared from. */
	private static int panelColor(Context dialogContext) {
		final int backgroundId = attr(dialogContext, android.R.attr.windowBackground);
		final Drawable background = ResourcesCompat.getDrawable(dialogContext.getResources(), backgroundId, dialogContext.getTheme());
		assertTrue("panel is not an inset shape: " + background, background instanceof InsetDrawable);

		final Drawable shape = ((InsetDrawable) background).getDrawable();
		assertTrue("panel inset does not wrap a shape: " + shape, shape instanceof GradientDrawable);

		final ColorStateList fill = ((GradientDrawable) shape).getColor();
		assertNotNull("panel shape has no fill colour", fill);
		return fill.getDefaultColor();
	}

	@Test
	@Config(qualifiers = "night")
	public void darkPreferenceDialogsMatchTheDialogsTheAppBuildsItself() {
		final Context dialog = dialogTheme();

		assertNotEquals("panel fell back to appcompat's white dialog background", APPCOMPAT_FALLBACK_PANEL, panelColor(dialog));

		assertEquals(attrColor(dialog, com.google.android.material.R.attr.colorSurfaceContainerHigh), panelColor(dialog));
	}

	@Test
	@Config(qualifiers = "notnight")
	public void lightPreferenceDialogsMatchTheDialogsTheAppBuildsItself() {
		final Context dialog = dialogTheme();

		assertEquals(attrColor(dialog, com.google.android.material.R.attr.colorSurfaceContainerHigh), panelColor(dialog));
	}

	/**
	 * {@code MaterialAlertDialogBuilder} resolves {@code materialAlertDialogTheme}, not {@code alertDialogTheme}, and falls back to the latter only
	 * when the former is missing. Every dialog the app builds itself rides on that, so the two must stay separate.
	 */
	@Test
	public void theAppsOwnDialogsKeepTheirOwnTheme() {
		assertEquals(com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog,
		             attr(appTheme(), com.google.android.material.R.attr.materialAlertDialogTheme));
	}

	/**
	 * The M3 dialog layout arrives through {@code alertDialogStyle}, and appcompat's {@code AlertController} inflates it by id. A layout missing one
	 * of those ids fails at show time rather than at build time, which no colour assertion above would catch.
	 */
	@Test
	@Config(qualifiers = "night")
	public void theListDialogAndroidxPreferenceBuildsStillInflates() {
		try (var controller = Robolectric.buildActivity(SettingsActivity.class)) {
			final FragmentActivity activity = controller.setup().get();
			final Dialog dialog = new AlertDialog.Builder(activity)
				.setTitle(R.string.pref_title_theme)
				.setSingleChoiceItems(R.array.theme_entries, 0, (d, which) -> { })
				.setPositiveButton(android.R.string.ok, null)
				.setNegativeButton(android.R.string.cancel, null)
				.create();
			dialog.show();

			assertNotNull("no title panel", dialog.findViewById(androidx.appcompat.R.id.alertTitle));
			assertNotNull("no button panel", dialog.findViewById(androidx.appcompat.R.id.buttonPanel));

			final ListView entries = dialog.findViewById(androidx.appcompat.R.id.select_dialog_listview);
			assertNotNull("no entry list", entries);
			assertEquals(3, entries.getCount());
		}
	}
}
