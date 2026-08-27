package com.almothafar.simplebatterynotifier.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.TwoStatePreference;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.ui.preference.RingtonePreference;
import com.almothafar.simplebatterynotifier.util.AppPrefs;
import com.almothafar.simplebatterynotifier.util.BatteryPercentFormatter;
import com.almothafar.simplebatterynotifier.util.TemperatureUtils;
import com.almothafar.simplebatterynotifier.ui.preference.TimePickerPreference;
import com.almothafar.simplebatterynotifier.ui.preference.TimePickerPreferenceDialogFragmentCompat;

import java.util.Set;

import static com.almothafar.simplebatterynotifier.util.BidiText.isolate;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Modern preference fragment using AndroidX Preferences
 * <p>
 * Handles preference UI, validation, and summary updates for all app settings.
 * Uses modern Activity Result API for ringtone picker and provides accessible
 * error feedback via Snackbar.
 */
public class GenericPreferenceFragment extends CardPreferenceFragment
		implements SharedPreferences.OnSharedPreferenceChangeListener {

	private static final String TAG = "GenericPreferenceFrag";

	/** Saved-state key holding {@link #pendingRingtoneKey} across a recreation — see {@link #onSaveInstanceState}. */
	private static final String PENDING_RINGTONE_KEY = "pendingRingtoneKey";

	private String pendingRingtoneKey;
	private ActivityResultLauncher<Intent> ringtonePickerLauncher;

	/**
	 * Called when the fragment is first created
	 * <p>
	 * Registers the ActivityResultLauncher for handling ringtone picker results
	 * using modern Activity Result API instead of deprecated startActivityForResult.
	 *
	 * @param savedInstanceState Saved state from previous instance
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (nonNull(savedInstanceState)) {
			pendingRingtoneKey = savedInstanceState.getString(PENDING_RINGTONE_KEY);
		}

		// Register the activity result launcher for the ringtone picker
		ringtonePickerLauncher = registerForActivityResult(
				new ActivityResultContracts.StartActivityForResult(),
				result -> {
					if (result.getResultCode() == Activity.RESULT_OK) {
						final Intent data = result.getData();
						if (nonNull(data)) {
							final Uri uri = extractRingtoneUri(data);
							applyPendingRingtonePick(nonNull(uri) ? uri.toString() : "");
						}
					}
				}
		);
	}

	/**
	 * Remember which sound picker is waiting on the system ringtone picker, so the answer still has somewhere to go if this screen does not survive it.
	 * <p>
	 * The picker is a separate activity, and while it is in front this fragment can be destroyed and rebuilt — a rotation, a font-size change, or the
	 * process being reclaimed, which is routine on the OEMs this app is most used on. Held only in a field, the waiting preference came back null, the
	 * result was dropped, and the user returned to a screen still showing the old sound with nothing to explain it (#305).
	 * <p>
	 * The key is saved rather than the {@link RingtonePreference} itself, and not only because a {@code Preference} cannot be parcelled: the hierarchy is
	 * re-inflated on the way back, so the object that was clicked no longer exists. A key survives that and re-resolves to the new instance.
	 *
	 * @param outState Bundle to save state into
	 */
	@Override
	public void onSaveInstanceState(@NonNull final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(PENDING_RINGTONE_KEY, pendingRingtoneKey);
	}

	/**
	 * Hand a sound the user just chose to whichever picker opened the system ringtone picker, resolved by key against the current hierarchy.
	 * <p>
	 * Resolving here rather than holding the clicked {@code Preference} is what makes the result survive a recreation (#305): after one, the saved key
	 * still names a preference on the rebuilt screen, while any retained reference points at a discarded object.
	 * <p>
	 * A result with nothing pending is dropped rather than guessed at — there is no safe default, and applying a sound to the wrong severity would be
	 * worse than the pick being lost.
	 *
	 * @param pickedUri the chosen sound URI, empty when the user chose "Silent"
	 */
	void applyPendingRingtonePick(String pickedUri) {
		final Preference pending = nonNull(pendingRingtoneKey) ? findPreference(pendingRingtoneKey) : null;
		pendingRingtoneKey = null;
		if (pending instanceof final RingtonePreference ringtone) {
			applyRingtonePick(requireContext(), ringtone, pickedUri);
		}
	}

	/**
	 * Extract ringtone URI from intent data using type-safe API for API 33+
	 * <p>
	 * Uses modern getParcelableExtra(String, Class) for API 33+ with fallback
	 * to deprecated method for API 26-32 (minSdk is 26).
	 *
	 * @param data Intent containing ringtone picker result
	 * @return Selected ringtone URI, or null if not available
	 */
	private Uri extractRingtoneUri(final Intent data) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
		} else {
			// Suppress deprecation warning - this is required for API < 33 compatibility (minSdk is 26)
			@SuppressWarnings("deprecation")
			final Uri fallbackUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
			return fallbackUri;
		}
	}

	/**
	 * Called when preferences should be created from XML resource
	 * <p>
	 * Loads the appropriate preference screen based on the category argument.
	 *
	 * @param savedInstanceState Saved state from previous instance
	 * @param rootKey            The root key of the preference hierarchy, or null
	 */
	@Override
	public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
		// Get category from arguments
		final Bundle args = getArguments();
		if (nonNull(args)) {
			final String category = args.getString("category");
			if (nonNull(category)) {
				if (category.equals(getString(R.string.pref_category_general))) {
					setPreferencesFromResource(R.xml.pref_general, rootKey);
					configureLanguagePreference();
				} else if (category.equals(getString(R.string.pref_category_alerts))) {
					setPreferencesFromResource(R.xml.pref_alerts, rootKey);
					configureTemperatureThreshold();
				} else if (category.equals(getString(R.string.pref_category_behaviour))) {
					setPreferencesFromResource(R.xml.pref_behaviour, rootKey);
				}
			}
		}
	}

	/**
	 * Wire up the app-language picker (System / Arabic / English).
	 * <p>
	 * The choice is applied and persisted by AndroidX per-app locales
	 * ({@link AppCompatDelegate#setApplicationLocales}, stored via the {@code AppLocalesMetadataHolderService}
	 * declared in the manifest), so the {@link ListPreference} itself is non-persistent — it only
	 * reflects and triggers the locale. Its value is seeded from the currently applied locale so the
	 * selection stays in sync when the screen is reopened.
	 */
	private void configureLanguagePreference() {
		final ListPreference pref = findPreference(getString(R.string._pref_key_language));
		if (isNull(pref)) {
			return;
		}

		// Seed the selection from the currently applied app locale ("" = follow the system).
		final LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
		pref.setValue(current.isEmpty() ? "" : current.get(0).getLanguage());

		pref.setOnPreferenceChangeListener((preference, newValue) -> {
			final String tag = (String) newValue;
			final LocaleListCompat locales = TextUtils.isEmpty(tag)
					? LocaleListCompat.getEmptyLocaleList()   // follow the system language
					: LocaleListCompat.forLanguageTags(tag);
			// Applies immediately (recreates activities); the metadata service persists it across restarts.
			AppCompatDelegate.setApplicationLocales(locales);
			return true;
		});
	}

	/**
	 * Configure the high-temperature threshold slider to display the user's temperature unit.
	 * <p>
	 * The value is stored canonically in °C (so the receiver compares without knowing the unit and
	 * changing the unit can't corrupt it). When the user prefers Fahrenheit the slider shows °F,
	 * converting on display and persisting back to °C. The preference is made non-persistent so its
	 * raw °F display value isn't written to the °C key.
	 */
	private void configureTemperatureThreshold() {
		final SeekBarPreference pref = findPreference(getString(R.string._pref_key_high_temperature_threshold));
		if (isNull(pref)) {
			return;
		}
		final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		if (isNull(prefs)) {
			return;
		}

		final boolean fahrenheit = TemperatureUtils.isFahrenheit(requireContext());
		final int storedCelsius = prefs.getInt(
				getString(R.string._pref_key_high_temperature_threshold),
				TemperatureUtils.DEFAULT_HIGH_TEMP_THRESHOLD_C);

		pref.setPersistent(false); // We persist °C ourselves; the slider shows the user's unit
		if (fahrenheit) {
			pref.setMin(TemperatureUtils.celsiusToFahrenheit(TemperatureUtils.MIN_HIGH_TEMP_THRESHOLD_C));
			pref.setMax(TemperatureUtils.celsiusToFahrenheit(TemperatureUtils.MAX_HIGH_TEMP_THRESHOLD_C));
			pref.setValue(TemperatureUtils.celsiusToFahrenheit(storedCelsius));
		} else {
			pref.setMin(TemperatureUtils.MIN_HIGH_TEMP_THRESHOLD_C);
			pref.setMax(TemperatureUtils.MAX_HIGH_TEMP_THRESHOLD_C);
			pref.setValue(storedCelsius);
		}

		pref.setOnPreferenceChangeListener((preference, newValue) -> {
			final int displayValue = (Integer) newValue;
			final int celsius = fahrenheit ? TemperatureUtils.fahrenheitToCelsius(displayValue) : displayValue;
			prefs.edit().putInt(getString(R.string._pref_key_high_temperature_threshold), celsius).apply();
			return true; // Accept the new display value
		});

		pref.setSummary(pref.getValue() + temperatureUnitSuffix(fahrenheit));
	}

	/**
	 * @param fahrenheit whether the user's display unit is Fahrenheit
	 * @return " °F" or " °C", localized
	 */
	private String temperatureUnitSuffix(final boolean fahrenheit) {
		return " " + getString(fahrenheit ? R.string.fahrenheit_short : R.string.celsius_short);
	}

	/**
	 * Display custom dialog for TimePickerPreference
	 * <p>
	 * Note: setTargetFragment is deprecated but still required by PreferenceDialogFragmentCompat.
	 * The AndroidX Preference library has not yet provided an alternative.
	 *
	 * @param preference The preference requesting the dialog
	 */
	@Override
	public void onDisplayPreferenceDialog(@NonNull final Preference preference) {
		// Handle custom TimePickerPreference dialog
		if (preference instanceof TimePickerPreference) {
			final DialogFragment dialogFragment = TimePickerPreferenceDialogFragmentCompat
					.newInstance(preference.getKey());

			// Set target fragment (required for PreferenceDialogFragmentCompat)
			// Note: setTargetFragment is deprecated but still required by PreferenceDialogFragmentCompat
			//noinspection deprecation
			dialogFragment.setTargetFragment(this, 0);
			dialogFragment.show(getParentFragmentManager(),
			                    "androidx.preference.PreferenceFragment.DIALOG");
		} else {
			super.onDisplayPreferenceDialog(preference);
		}
	}

	/**
	 * Register preference change listener and initialize summaries
	 */
	@Override
	public void onResume() {
		super.onResume();
		// Register preference change listener
		final PreferenceScreen preferenceScreen = getPreferenceScreen();
		if (nonNull(preferenceScreen)) {
			final SharedPreferences sharedPreferences = preferenceScreen.getSharedPreferences();
			if (nonNull(sharedPreferences)) {
				sharedPreferences.registerOnSharedPreferenceChangeListener(this);
			}
		}
		initSummary();
	}

	/**
	 * Unregister preference change listener to prevent memory leaks
	 */
	@Override
	public void onPause() {
		super.onPause();
		// Unregister preference change listener
		final PreferenceScreen preferenceScreen = getPreferenceScreen();
		if (nonNull(preferenceScreen)) {
			final SharedPreferences sharedPreferences = preferenceScreen.getSharedPreferences();
			if (nonNull(sharedPreferences)) {
				sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
			}
		}
	}

	/**
	 * Called when a shared preference is changed
	 * <p>
	 * Updates the preference summary to reflect the new value.
	 *
	 * @param sharedPreferences The SharedPreferences that received the change
	 * @param key               The key of the preference that was changed
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// Update summary when preference changes
		final Preference pref = findPreference(key);
		updatePreferencesSummary(sharedPreferences, pref);

		// The Vibrate toggle and the per-severity sound picks are baked into the alert channels, which Android caches, so
		// a change to one has to recreate them under new versioned IDs to take effect (#153, #286). Which preferences
		// those are, and the recreation itself, both belong to the channel registry — this screen only reports the change.
		NotificationService.refreshAlertChannelsIfAffected(requireContext(), key);
	}

	/**
	 * Update a preference summary based on its type and value
	 * <p>
	 * Delegates to specialized methods for each preference type to keep
	 * the method focused and maintainable.
	 *
	 * @param sharedPreferences The SharedPreferences containing preference values
	 * @param pref              The preference to update
	 */
	protected void updatePreferencesSummary(final SharedPreferences sharedPreferences, final Preference pref) {
		switch (pref) {
			case null -> {
			}
			case ListPreference listPref -> updateListPreferenceSummary(listPref);
			case EditTextPreference editTextPref -> updateEditTextPreferenceSummary(editTextPref);
			case SeekBarPreference seekBarPref -> updateSeekBarPreferenceSummary(seekBarPref);
			case MultiSelectListPreference mlistPref -> updateMultiSelectListPreferenceSummary(mlistPref);
			case RingtonePreference ringtonePref -> updateRingtonePreferenceSummary(sharedPreferences, ringtonePref);
			case TimePickerPreference timePickerPref -> updateTimePickerPreferenceSummary(sharedPreferences, timePickerPref);
			default -> {
				// No summary update needed for other preference types
			}
		}
	}

	/**
	 * Update summary for ListPreference
	 * <p>
	 * Skipped when a SummaryProvider is set (e.g. useSimpleSummaryProvider): calling
	 * setSummary() in that case throws IllegalStateException, and the provider already
	 * keeps the summary in sync with the selected entry.
	 */
	private void updateListPreferenceSummary(final ListPreference listPref) {
		if (isNull(listPref.getSummaryProvider())) {
			listPref.setSummary(listPref.getEntry());
		}
	}

	/**
	 * Update summary for EditTextPreference
	 * <p>
	 * Only updates if no custom SummaryProvider is already set.
	 */
	private void updateEditTextPreferenceSummary(final EditTextPreference editTextPref) {
		if (isNull(editTextPref.getSummaryProvider())) {
			editTextPref.setSummary(editTextPref.getText());
		}
	}

	/**
	 * Update summary for SeekBarPreference
	 * <p>
	 * Adds the temperature-unit suffix for the high-temperature threshold, and the wording that follows the charge target (#263).
	 */
	private void updateSeekBarPreferenceSummary(SeekBarPreference seekBarPref) {
		final String key = seekBarPref.getKey();
		if (isNull(key)) {
			return;
		}
		if (key.equals(getString(R.string._pref_key_high_temperature_threshold))) {
			seekBarPref.setSummary(seekBarPref.getValue() + temperatureUnitSuffix(TemperatureUtils.isFahrenheit(requireContext())));
		} else if (key.equals(getString(R.string._pref_key_charge_target))) {
			applyChargeTargetWording(seekBarPref);
		}
	}

	/**
	 * Apply a sound the user just chose in the system ringtone picker: persist it on the preference, and re-create the alert channels so it becomes
	 * audible.
	 * <p>
	 * The refresh is reported from here rather than left to {@link #onSharedPreferenceChanged}, which is where every other channel setting reports its
	 * change from. The Activity Result API delivers a picker result once the fragment reaches STARTED, and this screen only registers its
	 * preference-change listener in {@code onResume} — so the write below lands in the one window where nothing is listening, and the pick was saved to
	 * preferences without ever reaching a channel (#303). Since Android freezes a channel's sound at creation and only a <em>change</em> re-versions the
	 * channels, that made the pick permanently inaudible rather than merely late.
	 * <p>
	 * Reported unconditionally, exactly as {@link #onSharedPreferenceChanged} reports the Vibrate toggle. Whether a setting is worth re-creating the
	 * channels for is decided by key, in {@code NotificationChannels.affectsAlertChannels}, and never by comparing values at a call site — a second
	 * opinion here would be the two report sites disagreeing about what a refresh means.
	 *
	 * @param context    context to resolve the channel settings against
	 * @param preference the picker that was clicked
	 * @param pickedUri  the chosen sound URI, empty when the user chose "Silent"
	 */
	static void applyRingtonePick(Context context, RingtonePreference preference, String pickedUri) {
		preference.setRingtoneUri(pickedUri);
		NotificationService.refreshAlertChannelsIfAffected(context, preference.getKey());
	}

	/**
	 * The charge-target slider's summary line, e.g. {@code "Alert at 90% — nearly done, better for battery life"}.
	 * <p>
	 * Split out from the preference so the wording can be asserted without inflating the screen: the percentage's bidi isolation (#275) is invisible to a
	 * {@code contains} check and only shows up once the summary is laid out.
	 *
	 * @param context context to resolve the strings against
	 * @param target  the clamped charge target, in whole percent
	 *
	 * @return the summary for the current locale
	 */
	static String chargeTargetSummary(Context context, int target) {
		// Formatted rather than passed as an int: getString formats with the configuration locale, so a %1$d prints ٩٠ on ar-EG/ar-SA/ar-JO. Numbers stay
		// Western in every locale (#96), and isolated so the formatter's '%' can't reorder away from its digits inside the Arabic summary (#275).
		return context.getString(AppPrefs.targetIsAFullCharge(target) ? R.string.charge_target_summary_full : R.string.charge_target_summary,
				isolate(BatteryPercentFormatter.formatWhole(target)));
	}

	/**
	 * Say what the chosen charge target will actually do (#263).
	 * <p>
	 * Below a full charge the alert is not about a full battery, so the whole row above the slider stops claiming it is — title and summary both — and the
	 * slider spells out the level it will alert at. At the maximum every line reads exactly as it did before the target was configurable.
	 * <p>
	 * The level shown is {@link AppPrefs#chargeTarget}, not the raw slider value: that accessor's clamp is what the alert engine and the temperature range act
	 * on, so reading anything else here would let the screen advertise a target nothing else honours.
	 * <p>
	 * Called from the slider's summary update, which {@link #initSummary()} runs on every resume — so the wording is re-applied on screen open, rotation and
	 * locale change, not only when the value moves.
	 *
	 * @param slider the charge-target slider
	 */
	private void applyChargeTargetWording(SeekBarPreference slider) {
		final int target = AppPrefs.chargeTarget(requireContext());
		final boolean fullCharge = AppPrefs.targetIsAFullCharge(target);
		slider.setSummary(chargeTargetSummary(requireContext(), target));

		final Preference alertSwitch = findPreference(getString(R.string._pref_key_notify_for_full_level));
		if (alertSwitch instanceof final TwoStatePreference toggle) {
			toggle.setTitle(fullCharge ? R.string.notify_for_full_level : R.string.notify_when_almost_full);
			// The summary sits directly under the title, so leaving it saying "Full Level" while the title says "almost full" makes one row describe itself two
			// ways.
			toggle.setSummaryOn(getString(fullCharge
					? R.string.notify_for_full_level_summary_on
					: R.string.notify_when_almost_full_summary_on));
			toggle.setSummaryOff(getString(fullCharge
					? R.string.notify_for_full_level_summary_off
					: R.string.notify_when_almost_full_summary_off));
		}
	}

	/**
	 * Update summary for MultiSelectListPreference
	 * <p>
	 * Shows selected items separated by semicolons.
	 */
	private void updateMultiSelectListPreferenceSummary(final MultiSelectListPreference mlistPref) {
		final StringBuilder summaryBuilder = new StringBuilder();
		final Set<String> values = mlistPref.getValues();

		int count = 0;
		for (final String value : values) {
			final int index = mlistPref.findIndexOfValue(value);
			if (index >= 0 && nonNull(mlistPref.getEntries())) {
				if (count > 0) {
					summaryBuilder.append("; ");
				}
				summaryBuilder.append(mlistPref.getEntries()[index]);
				count++;
			}
		}
		mlistPref.setSummary(summaryBuilder.toString());
	}

	/**
	 * Update summary for RingtonePreference
	 * <p>
	 * Shows the ringtone title, or the URI if title cannot be retrieved.
	 */
	private void updateRingtonePreferenceSummary(SharedPreferences sharedPreferences, RingtonePreference ringtonePref) {
		final String uri = sharedPreferences.getString(ringtonePref.getKey(), null);
		if (nonNull(uri) && !uri.isEmpty()) {
			try {
				final Ringtone ringtone = RingtoneManager.getRingtone(ringtonePref.getContext(), Uri.parse(uri));
				if (nonNull(ringtone)) {
					ringtonePref.setSummary(ringtone.getTitle(ringtonePref.getContext()));
				}
			} catch (RuntimeException e) {
				// Resolving the ringtone can fail (e.g. SecurityException on a revoked media URI):
				// fall back to showing the raw URI, but don't swallow it silently.
				Log.w(TAG, "Could not resolve ringtone title for " + uri + "; showing URI", e);
				ringtonePref.setSummary(uri);
			}
		}
	}

	/**
	 * Update summary for TimePickerPreference
	 * <p>
	 * Shows the selected time value.
	 */
	private void updateTimePickerPreferenceSummary(SharedPreferences sharedPreferences, TimePickerPreference timePickerPref) {
		final String value = sharedPreferences.getString(timePickerPref.getKey(), "");
		if (nonNull(value) && !value.isEmpty()) {
			timePickerPref.setSummary(value);
		}
	}

	/**
	 * Initialize summaries for all preferences
	 * <p>
	 * Called when the fragment is resumed to ensure all preference summaries
	 * are up to date.
	 */
	protected void initSummary() {
		final PreferenceScreen screen = getPreferenceScreen();
		if (nonNull(screen)) {
			final SharedPreferences sharedPrefs = screen.getSharedPreferences();
			for (int i = 0; i < screen.getPreferenceCount(); i++) {
				initPreferencesSummary(sharedPrefs, screen.getPreference(i));
			}
		}
	}

	/**
	 * Initialize the summary for a single preference (recursively for categories)
	 * <p>
	 * Sets up click listeners for custom preferences and validation for battery levels.
	 *
	 * @param sharedPreferences The SharedPreferences containing preference values
	 * @param p                 The preference to initialize
	 */
	protected void initPreferencesSummary(final SharedPreferences sharedPreferences, final Preference p) {
		if (p instanceof final PreferenceCategory pCat) {
			// Recursively initialize category children
			for (int i = 0; i < pCat.getPreferenceCount(); i++) {
				initPreferencesSummary(sharedPreferences, pCat.getPreference(i));
			}
		} else {
			updatePreferencesSummary(sharedPreferences, p);

			// Set up click listener for ringtone preferences
			if (p instanceof final RingtonePreference ringtonePref) {
				setupRingtonePreferenceListener(ringtonePref);
			}
		}
	}

	/**
	 * Set up click listener for ringtone preference
	 * <p>
	 * Launches the ringtone picker when the preference is clicked.
	 *
	 * @param ringtonePref The ringtone preference to configure
	 */
	private void setupRingtonePreferenceListener(final RingtonePreference ringtonePref) {
		ringtonePref.setOnPreferenceClickListener(pref -> {
			pendingRingtoneKey = pref.getKey();
			final Intent intent = ringtonePref.createRingtonePickerIntent();
			ringtonePickerLauncher.launch(intent);
			return true;
		});
	}

}
