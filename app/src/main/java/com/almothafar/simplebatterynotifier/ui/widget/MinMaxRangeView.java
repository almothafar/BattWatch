package com.almothafar.simplebatterynotifier.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.almothafar.simplebatterynotifier.R;

/**
 * The two-column "min / max" mini-table (caption above value) used by the Battery Insights cards that
 * show a spread rather than a single figure — the measured capacity (#116) and the temperature range
 * (#260).
 * <p>
 * Both cards drew the same eight nested views inline before this existed. An {@code <include>} cannot
 * carry them: the two instances live in the same layout, so their child ids would collide and the
 * activity's {@code findViewById} would resolve both cards to whichever came first. A compound view
 * scopes the lookup to its own subtree, and hands each card a single {@link #setRange} call instead of
 * two {@code TextView} fields.
 * <p>
 * The captions are fixed; the caller styles the values through {@code rangeValueTextSize} and
 * {@code rangeValueBold}, because the capacity card shows its spread underneath a larger primary
 * value while the temperature card's spread <em>is</em> its primary value.
 */
public class MinMaxRangeView extends LinearLayout {

	// Matches the measured-capacity card, the smaller of the two callers.
	private static final float DEFAULT_VALUE_TEXT_SIZE_SP = 14f;

	private final TextView minValue;
	private final TextView maxValue;

	public MinMaxRangeView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// The row shape belongs to the view, not to every instance tag that uses it.
		setOrientation(HORIZONTAL);
		setBaselineAligned(false);
		setWeightSum(2);
		inflate(context, R.layout.view_min_max_range, this);

		minValue = findViewById(R.id.rangeMinValue);
		maxValue = findViewById(R.id.rangeMaxValue);
		applyValueStyle(context, attrs);
	}

	/**
	 * Shows a spread. Both ends are set together — half a range is never meaningful.
	 *
	 * @param min the low end, already formatted for display
	 * @param max the high end, already formatted for display
	 */
	public void setRange(CharSequence min, CharSequence max) {
		minValue.setText(min);
		maxValue.setText(max);
	}

	/**
	 * Shows the placeholder in both cells, for a range that has nothing recorded in it yet.
	 */
	public void setPending() {
		minValue.setText(R.string.battery_value_pending);
		maxValue.setText(R.string.battery_value_pending);
	}

	private void applyValueStyle(Context context, AttributeSet attrs) {
		final float defaultSizePx = TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_SP,
				DEFAULT_VALUE_TEXT_SIZE_SP,
				getResources().getDisplayMetrics());

		final TypedArray styled = context.obtainStyledAttributes(attrs, R.styleable.MinMaxRangeView);
		final float valueSizePx = styled.getDimension(R.styleable.MinMaxRangeView_rangeValueTextSize, defaultSizePx);
		final boolean bold = styled.getBoolean(R.styleable.MinMaxRangeView_rangeValueBold, false);
		styled.recycle();

		minValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, valueSizePx);
		maxValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, valueSizePx);
		if (bold) {
			minValue.setTypeface(minValue.getTypeface(), Typeface.BOLD);
			maxValue.setTypeface(maxValue.getTypeface(), Typeface.BOLD);
		}
	}
}
