package com.almothafar.simplebatterynotifier;

import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertTrue;

/**
 * Resolves theme attributes for the tests that assert on the app's palette, so they read the same value the framework hands a widget rather than the literal
 * a colour resource happens to hold.
 * <p>
 * Shared because two suites need it for unrelated reasons and would otherwise carry the same helper twice: {@code PreferenceDialogThemeTest} reads the panel
 * behind a preference dialog (#335), and {@code SnackbarActionContrastTest} reads the pair Material paints a snackbar from (#333). Both go through
 * {@link #appTheme()} rather than the bare application context, because an unthemed context resolves none of these and would fail in a way that looks like
 * the value being wrong.
 */
public final class ThemeAttributes {

	private ThemeAttributes() {
		// Utility class - prevent instantiation
	}

	/** The application context wearing {@code AppTheme}, which is what every activity in the app is themed with. */
	public static Context appTheme() {
		return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme);
	}

	/**
	 * The colour an attribute resolves to, as an ARGB int.
	 *
	 * @param context a themed context — see {@link #appTheme()}
	 * @param attrId  the attribute to resolve
	 *
	 * @return the resolved colour
	 */
	public static int color(Context context, int attrId) {
		final TypedValue value = new TypedValue();

		assertTrue("attribute unresolved", context.getTheme().resolveAttribute(attrId, value, true));
		return value.data;
	}

	/**
	 * The resource an attribute points at, for the attributes that name a style or drawable rather than carrying a value.
	 *
	 * @param context a themed context — see {@link #appTheme()}
	 * @param attrId  the attribute to resolve
	 *
	 * @return the resource id, or the raw data when the attribute holds a value rather than a reference
	 */
	public static int reference(Context context, int attrId) {
		final TypedValue value = new TypedValue();

		assertTrue("attribute unresolved", context.getTheme().resolveAttribute(attrId, value, true));
		return value.type == TypedValue.TYPE_REFERENCE || value.resourceId != 0 ? value.resourceId : value.data;
	}
}
