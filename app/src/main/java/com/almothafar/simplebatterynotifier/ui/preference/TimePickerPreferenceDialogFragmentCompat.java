package com.almothafar.simplebatterynotifier.ui.preference;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TimePicker;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.almothafar.simplebatterynotifier.util.GeneralHelper;

/**
 * Dialog fragment for TimePickerPreference
 * Provides a time picker dialog for selecting hours and minutes
 */
public class TimePickerPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {
	private TimePicker timePicker;

	/**
	 * Create a new instance of TimePickerPreferenceDialogFragmentCompat
	 *
	 * @param key The preference key
	 * @return New dialog fragment instance
	 */
	public static TimePickerPreferenceDialogFragmentCompat newInstance(final String key) {
		final TimePickerPreferenceDialogFragmentCompat fragment = new TimePickerPreferenceDialogFragmentCompat();
		final Bundle args = new Bundle(1);
		args.putString(ARG_KEY, key);
		fragment.setArguments(args);
		return fragment;
	}

	/**
	 * Create the dialog view with a TimePicker widget
	 *
	 * @param context The context for creating the view
	 * @return The TimePicker view
	 */
	@Override
	protected View onCreateDialogView(@NonNull final Context context) {
		timePicker = new TimePicker(context);
		timePicker.setIs24HourView(DateFormat.is24HourFormat(context));
		return timePicker;
	}

	/**
	 * Bind the preference value to the TimePicker
	 *
	 * @param view The dialog view
	 */
	@Override
	protected void onBindDialogView(@NonNull final View view) {
		super.onBindDialogView(view);

		final TimePickerPreference preference = (TimePickerPreference) getPreference();

		// Set the time to the TimePicker
		timePicker.setHour(preference.getHour());
		timePicker.setMinute(preference.getMinute());
	}

	/**
	 * Handle dialog close and save the selected time if positive button was clicked
	 *
	 * @param positiveResult True if the positive button was clicked
	 */
	@Override
	public void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			// Get the current values from the TimePicker
			savePickedTime((TimePickerPreference) getPreference(), timePicker.getHour(), timePicker.getMinute());
		}
	}

	/**
	 * Write a picked time to the preference in the canonical persisted form.
	 * <p>
	 * Formatting goes through {@link GeneralHelper#formatTime(int, int)} rather than a local
	 * {@code String.format}: the default locale under Arabic renders Eastern Arabic numerals, so a
	 * hand-rolled format hands {@code ٠٨:٣٠} to the change listeners and to {@code setTime} (issue
	 * #241 — the same defect as #154, which fixed the helper but not this call site). Split out from
	 * {@link #onDialogClosed(boolean)} so the writing path is unit-testable without a live dialog.
	 *
	 * @param preference the preference being edited
	 * @param hour       picked hour of day (0-23)
	 * @param minute     picked minute of hour (0-59)
	 */
	static void savePickedTime(TimePickerPreference preference, int hour, int minute) {
		final String time = GeneralHelper.formatTime(hour, minute);

		if (preference.callChangeListener(time)) {
			preference.setTime(time);
		}
	}
}
