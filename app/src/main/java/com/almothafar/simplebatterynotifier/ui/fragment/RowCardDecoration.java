package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.almothafar.simplebatterynotifier.R;

import static java.util.Objects.isNull;

/**
 * Paints the notification-details picker's rows as one rounded card (#358), the look {@code PreferenceCardDecoration} gives the preference screens. The rows
 * are items of the one list the whole page is, beside the preview and the header, so a row dragged to the screen's edge scrolls the page along with it.
 * <p>
 * The card is painted where the rows are laid out, never where they are drawn: a dragged row floats under the finger, and rows moved by a tap glide between
 * places, while the card behind them keeps still. A dragged row paints its own background instead, so it covers the rows it passes.
 */
final class RowCardDecoration extends RecyclerView.ItemDecoration {

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF card = new RectF();
	private final float radius;
	private final int firstRow;

	/**
	 * @param context  context to resolve the card's colour and corners against
	 * @param firstRow the place of the first row; the card runs from it to the end of the list
	 */
	RowCardDecoration(Context context, int firstRow) {
		paint.setColor(ContextCompat.getColor(context, R.color.settings_group_card));
		radius = context.getResources().getDimension(R.dimen.settings_card_corner_radius);
		this.firstRow = firstRow;
	}

	/**
	 * Paint the card behind the rows in view. Where it runs on past the screen's top or bottom edge, its corner is taken off the screen too, so scrolling never
	 * shows a corner in the middle of the card.
	 */
	@Override
	public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		final int lastRow = state.getItemCount() - 1;
		// Any row in view, for the card's sides: the rows all share them.
		View sides = null;
		float top = Float.MAX_VALUE;
		float bottom = -Float.MAX_VALUE;
		boolean showsFirst = false;
		boolean showsLast = false;
		for (int index = 0; index < parent.getChildCount(); index++) {
			final View row = parent.getChildAt(index);
			final int position = parent.getChildLayoutPosition(row);
			if (position < firstRow) {
				continue;
			}
			sides = row;
			// getTop and getBottom leave out the translation a drag or a move animation adds: they are the row's place.
			top = Math.min(top, row.getTop());
			bottom = Math.max(bottom, row.getBottom());
			showsFirst |= position == firstRow;
			showsLast |= position == lastRow;
		}
		if (isNull(sides)) {
			return;
		}
		card.set(sides.getLeft(), showsFirst ? top : -radius, sides.getRight(), showsLast ? bottom : parent.getHeight() + radius);
		canvas.drawRoundRect(card, radius, radius, paint);
	}
}
