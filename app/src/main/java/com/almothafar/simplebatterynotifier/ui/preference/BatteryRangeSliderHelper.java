package com.almothafar.simplebatterynotifier.ui.preference;

import android.content.Context;
import android.widget.TextView;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.LevelThresholds;
import com.almothafar.simplebatterynotifier.util.BatteryPercentFormatter;
import com.google.android.material.slider.RangeSlider;

import static com.almothafar.simplebatterynotifier.util.BidiText.isolate;
import static java.util.Objects.nonNull;

/**
 * Shared configuration for the combined critical/warning battery-level {@link RangeSlider}.
 * <p>
 * The same two-thumb control appears in two places — the settings screen
 * ({@link BatteryRangeSliderPreference}) and the in-fly control on the home screen — so the
 * bounds, step, minimum thumb separation, and the "{@code N%}" label formatting live here as the
 * single source of truth. The left thumb is the critical level, the right thumb is the warning
 * level, and {@link #MIN_SEPARATION} guarantees critical always stays below warning.
 */
public final class BatteryRangeSliderHelper {

	/** Lowest value the critical thumb can reach (the combined track's start). */
	public static final int LEVEL_FROM = 10;
	/** Highest value the warning thumb can reach (the combined track's end). */
	public static final int LEVEL_TO = 50;
	/** Minimum gap kept between the critical and warning thumbs, in percent. */
	public static final int MIN_SEPARATION = 5;

	private BatteryRangeSliderHelper() {
		// Utility class
	}

	/**
	 * Apply the shared bounds, integer step, minimum thumb separation, and a "{@code N%}" label
	 * formatter to a slider. Does not set the thumb values — callers set those after clamping via
	 * {@link #clampPair(LevelThresholds, int, int, int)}.
	 *
	 * @param slider        the range slider to configure
	 * @param from          combined track start (critical floor)
	 * @param to            combined track end (warning ceiling)
	 * @param minSeparation minimum gap between the two thumbs, in value units
	 */
	public static void configure(RangeSlider slider, int from, int to, int minSeparation) {
		slider.setValueFrom(from);
		slider.setValueTo(to);
		slider.setStepSize(1f);
		slider.setMinSeparationValue(minSeparation);
		// Show the dragged thumb's value as a whole percentage (e.g. "20%") instead of "20.0".
		slider.setLabelFormatter(BatteryRangeSliderHelper::labelFor);
	}

	/**
	 * The dragged thumb's label: the value as a whole percentage (e.g. {@code "20%"}) rather than the raw {@code "20.0"}.
	 * <p>
	 * {@link BatteryPercentFormatter} is the whole label — it supplies both the digits and the {@code '%'}, and keeps the digits Western in every locale
	 * (#96/#273). There is no string resource behind this: one existed, but once the formatter owned the {@code '%'} it had no text left but its own
	 * placeholder, so it was removed rather than left as an identity format for a reader to puzzle over.
	 *
	 * @param value the thumb's value
	 *
	 * @return the label, in Western digits whatever the app language
	 */
	static String labelFor(float value) {
		return BatteryPercentFormatter.formatWhole(Math.round(value));
	}

	/**
	 * Applies the critical/warning captions that sit under the slider, in the single form both surfaces show them.
	 * <p>
	 * The settings screen and the home screen each own a copy of this pair of captions. Keeping the wording and the formatting here means a change lands once
	 * rather than twice — the two copies drifting apart is how a display defect survives a fix in one of them (#241 after #154).
	 * <p>
	 * The pair arrives as a {@link LevelThresholds} rather than two loose ints. Both captions take the same type and sit next to each other in the argument
	 * list, so a transposed call would compile and simply label each threshold with the other's number; the record names them instead, and keeps the signature
	 * at four parameters.
	 *
	 * @param context         context to resolve the caption strings against
	 * @param criticalCaption the critical caption view, or null when the layout omits it
	 * @param warningCaption  the warning caption view, or null when the layout omits it
	 * @param levels          the (critical, warning) pair to label, in percent
	 */
	public static void applyCaptions(Context context, TextView criticalCaption, TextView warningCaption, LevelThresholds levels) {
		// Formatted, not handed to getString as an int: getString formats with the configuration locale, so a %1$d renders ٢٠ under ar-EG (#96/#273). Isolated
		// so the '%' stays on the number's side of the Arabic caption word ("حرج 20%", not "حرج %20") — #275.
		if (nonNull(criticalCaption)) {
			criticalCaption.setText(context.getString(R.string.battery_range_caption_critical, isolate(BatteryPercentFormatter.formatWhole(levels.critical()))));
		}
		if (nonNull(warningCaption)) {
			warningCaption.setText(context.getString(R.string.battery_range_caption_warning, isolate(BatteryPercentFormatter.formatWhole(levels.warning()))));
		}
	}

	/**
	 * Clamp a {@link LevelThresholds} pair into {@code [from, to]} while keeping the two levels at least
	 * {@code minSeparation} apart, so persisted or corrupted values can always be shown on the slider
	 * without tripping its validation.
	 *
	 * @param levels        the raw (critical, warning) pair to reconcile
	 * @param from          combined track start (critical floor)
	 * @param to            combined track end (warning ceiling)
	 * @param minSeparation minimum gap between the two thumbs, in value units
	 *
	 * @return a clamped {@code (critical, warning)} pair the slider can always accept
	 */
	public static LevelThresholds clampPair(final LevelThresholds levels,
	                                        final int from, final int to, final int minSeparation) {
		int low = clamp(levels.critical(), from, to);
		int high = clamp(levels.warning(), from, to);
		if (high - low < minSeparation) {
			// Push the warning thumb up first; if that hits the ceiling, pull critical back down.
			high = Math.min(to, low + minSeparation);
			low = Math.max(from, high - minSeparation);
		}
		return new LevelThresholds(low, high);
	}

	private static int clamp(final int value, final int lo, final int hi) {
		return Math.max(lo, Math.min(hi, value));
	}
}
