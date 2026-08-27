package com.almothafar.simplebatterynotifier.ui.preference;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import androidx.preference.Preference;

import com.almothafar.simplebatterynotifier.R;

import static com.almothafar.simplebatterynotifier.util.BidiText.isolate;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Custom RingtonePreference for AndroidX
 * Launches the system ringtone picker when clicked
 */
public class RingtonePreference extends Preference {
	private static final String TAG = "RingtonePreference";

	private int ringtoneType = RingtoneManager.TYPE_NOTIFICATION;
	private String currentRingtoneUri;

	/** The alerts this picker's severity bucket drives, shown after the sound name — see {@code R.styleable.RingtonePreference_scopeSummary} (#307). */
	private String scopeSummary;

	/**
	 * Constructor with all parameters
	 *
	 * @param context      The context
	 * @param attrs        Attribute set
	 * @param defStyleAttr Default style attribute
	 * @param defStyleRes  Default style resource
	 */
	public RingtonePreference(final Context context,
	                          final AttributeSet attrs,
	                          final int defStyleAttr,
	                          final int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		initialize(attrs);
	}

	/**
	 * Constructor with context, attributes, and style attribute
	 *
	 * @param context      The context
	 * @param attrs        Attribute set
	 * @param defStyleAttr Default style attribute
	 */
	public RingtonePreference(final Context context, final AttributeSet attrs, final int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initialize(attrs);
	}

	/**
	 * Constructor with context and attributes
	 *
	 * @param context The context
	 * @param attrs   Attribute set
	 */
	public RingtonePreference(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		initialize(attrs);
	}

	/**
	 * Constructor with context only
	 *
	 * @param context The context
	 */
	public RingtonePreference(final Context context) {
		super(context);
		initialize(null);
	}

	/**
	 * Create an intent to launch the system ringtone picker
	 *
	 * @return Intent configured for ringtone selection
	 */
	public Intent createRingtonePickerIntent() {
		final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType);
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getTitle());
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
		intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);

		// Set the current ringtone
		if (nonNull(currentRingtoneUri) && !currentRingtoneUri.isEmpty()) {
			intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentRingtoneUri));
		}

		return intent;
	}

	/**
	 * Get the ringtone type
	 *
	 * @return Ringtone type constant from RingtoneManager
	 */
	public int getRingtoneType() {
		return ringtoneType;
	}

	/**
	 * Get the current ringtone URI
	 *
	 * @return Current ringtone URI string
	 */
	public String getRingtoneUri() {
		return currentRingtoneUri;
	}

	/**
	 * Set the ringtone URI and persist it
	 *
	 * @param uri The ringtone URI to set
	 */
	public void setRingtoneUri(final String uri) {
		currentRingtoneUri = uri;
		persistString(uri);
		updateSummary();
	}

	/**
	 * Get the default value from XML attributes
	 *
	 * @param a     TypedArray containing the attribute values
	 * @param index Index of the default value
	 *
	 * @return The default ringtone URI string
	 */
	@Override
	protected Object onGetDefaultValue(final TypedArray a, final int index) {
		return a.getString(index);
	}

	/**
	 * Called when preference is attached to the preference hierarchy
	 * Ensures summary is updated when the preference is displayed
	 */
	@Override
	public void onAttached() {
		super.onAttached();
		// Ensure the summary is updated when a preference is attached
		if (currentRingtoneUri == null) {
			currentRingtoneUri = getPersistedString("");
		}
		updateSummary();
	}

	/**
	 * Set the initial value from preferences
	 *
	 * @param defaultValue The default value if no persisted value exists
	 */
	@Override
	protected void onSetInitialValue(final Object defaultValue) {
		String uri = getPersistedString(null);

		// Only apply default if preference has never been set (first install)
		// If uri is null, preference has never been set
		// If uri is "", user explicitly selected "None" - respect their choice
		if (uri == null) {
			if (nonNull(defaultValue) && !defaultValue.toString().isEmpty()) {
				uri = defaultValue.toString();
			} else {
				// Use the actual system default notification sound
				final Uri defaultNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				uri = nonNull(defaultNotificationUri) ? defaultNotificationUri.toString() : "";
			}
		}

		setRingtoneUri(uri);
	}

	/**
	 * Initialize the preference from attributes
	 *
	 * @param attrs Attribute set may be null
	 */
	private void initialize(final AttributeSet attrs) {
		if (nonNull(attrs)) {
			// Read ringtoneType attribute if present
			final int type = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "ringtoneType", -1);
			if (type != -1) {
				ringtoneType = type;
			}
			// Resolved through a TypedArray rather than read off the AttributeSet like ringtoneType above: scopeSummary is always a @string reference, and the
			// raw attribute value for one is the unresolved "@2131…" rather than the text. Not try-with-resources — TypedArray only became AutoCloseable in
			// API 31 and minSdk here is 26.
			final TypedArray styled = getContext().obtainStyledAttributes(attrs, R.styleable.RingtonePreference);
			try {
				scopeSummary = styled.getString(R.styleable.RingtonePreference_scopeSummary);
			} finally {
				styled.recycle();
			}
		}
	}

	/**
	 * Update the preference summary with the ringtone title
	 */
	private void updateSummary() {
		setSummary(withScope(selectedSoundName()));
	}

	/**
	 * The selected sound as the user would name it.
	 *
	 * @return the ringtone's title, or the "Silent" label when nothing is selected or it cannot be resolved
	 */
	private String selectedSoundName() {
		if (nonNull(currentRingtoneUri) && !currentRingtoneUri.isEmpty()) {
			try {
				final Ringtone ringtone = RingtoneManager.getRingtone(getContext(), Uri.parse(currentRingtoneUri));
				if (nonNull(ringtone)) {
					return ringtone.getTitle(getContext());
				}
			} catch (RuntimeException e) {
				// Resolving the ringtone can fail (e.g. SecurityException on a revoked media URI).
				Log.e(TAG, "Error loading ringtone for " + currentRingtoneUri, e);
			}
		}
		// Empty or null URI means no ringtone selected — matches the picker's own "Silent" option (#165)
		return getContext().getString(R.string.pref_ringtone_silent);
	}

	/**
	 * Append what this picker's bucket drives to the sound's name, so a row that sets two or three channels says so (#307). A picker that declares no scope
	 * keeps the bare sound name it has always shown.
	 * <p>
	 * The sound name is bidi-isolated (#275): it comes from the device's ringtone list and is usually Latin, so unisolated it reaches an Arabic reader with
	 * its words reordered against the surrounding prose.
	 *
	 * @param soundName the selected sound's display name
	 *
	 * @return the summary line to show on the preference row
	 */
	private String withScope(String soundName) {
		if (isNull(scopeSummary)) {
			return soundName;
		}
		return getContext().getString(R.string.pref_ringtone_summary_with_scope, isolate(soundName), scopeSummary);
	}
}
