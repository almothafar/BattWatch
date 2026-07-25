package com.almothafar.simplebatterynotifier.ui.preference;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * Robolectric tests for the dialog's save path (issue #241).
 * <p>
 * {@code GeneralHelperTest} already pins {@link com.almothafar.simplebatterynotifier.util.GeneralHelper#formatTime}
 * to Western digits, but that test passes whether or not this dialog calls it — which is exactly how
 * the #154 fix left a hand-rolled {@code String.format("%02d:%02d", ...)} here for another release.
 * These tests exercise the <em>call site</em> under an Arabic default locale, so the writer can't
 * silently stop using the shared helper again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TimePickerPreferenceDialogFragmentCompatTest {

	/**
	 * The Arabic locale that actually reproduces the bug. CLDR selects the {@code arab} numbering
	 * system only for region-bearing Arabic locales: under bare {@code "ar"} the zero digit is
	 * Western {@code '0'}, so a test written against {@code Locale.forLanguageTag("ar")} passes even
	 * with the defect present — which is how issue #241 survived the #154 fix.
	 */
	private static final Locale ARABIC_EASTERN_DIGITS = Locale.forLanguageTag("ar-EG");

	private Locale originalLocale;
	private Context context;

	@Before
	public void setUp() {
		originalLocale = Locale.getDefault();
		Locale.setDefault(ARABIC_EASTERN_DIGITS);
		// Premise first: without it these tests pass against the defect they exist to catch.
		assertEquals("guard locale no longer emits Eastern Arabic digits — pick one that does",
				"٠٨:٣٠", String.format("%02d:%02d", 8, 30));
		context = ApplicationProvider.getApplicationContext();
	}

	@After
	public void tearDown() {
		Locale.setDefault(originalLocale);
	}

	/**
	 * The value handed to the change listeners is the raw string the dialog built — nothing
	 * normalizes it on the way out, so this is where Eastern Arabic digits escape.
	 */
	@Test
	public void offersWesternDigitsToChangeListeners() {
		final TimePickerPreference preference = new TimePickerPreference(context);
		final List<Object> offered = new ArrayList<>();
		preference.setOnPreferenceChangeListener((pref, newValue) -> {
			offered.add(newValue);
			return true;
		});

		TimePickerPreferenceDialogFragmentCompat.savePickedTime(preference, 8, 30);

		assertEquals(List.of("08:30"), offered);
	}

	/**
	 * A listener that vetoes the change must leave the stored time untouched — the guard around
	 * {@code setTime} is part of the save path, so it is worth holding in place.
	 */
	@Test
	public void keepsPreviousTimeWhenChangeIsVetoed() {
		final TimePickerPreference preference = new TimePickerPreference(context);
		preference.setTime("06:30");
		preference.setOnPreferenceChangeListener((pref, newValue) -> false);

		TimePickerPreferenceDialogFragmentCompat.savePickedTime(preference, 8, 30);

		assertEquals(6, preference.getHour());
		assertEquals(30, preference.getMinute());
	}

	/**
	 * End to end through the preference: the picked time survives the round trip as the same
	 * hour/minute, and reads back in the canonical Western-digit form.
	 */
	@Test
	public void storesPickedTimeInCanonicalForm() {
		final TimePickerPreference preference = new TimePickerPreference(context);

		TimePickerPreferenceDialogFragmentCompat.savePickedTime(preference, 8, 30);

		assertEquals(8, preference.getHour());
		assertEquals(30, preference.getMinute());
		assertEquals("08:30", preference.getTime());
	}

	/**
	 * Midnight is the value a corrupt-pref reset falls back to, so pinning it here keeps a passing
	 * "00:00" from being mistaken for a real save in the other tests.
	 */
	@Test
	public void zeroPadsMidnight() {
		final TimePickerPreference preference = new TimePickerPreference(context);
		final List<Object> offered = new ArrayList<>();
		preference.setOnPreferenceChangeListener((pref, newValue) -> {
			offered.add(newValue);
			return true;
		});

		TimePickerPreferenceDialogFragmentCompat.savePickedTime(preference, 0, 0);

		assertEquals(List.of("00:00"), offered);
	}
}
