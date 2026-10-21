package com.almothafar.simplebatterynotifier.ui.fragment;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Scrolls the notification-details page under a row dragged to the screen's top or bottom edge (#358), so a row can be carried to a place out of sight on a
 * short screen or at a large font size.
 * <p>
 * The picker's list lays out every row inside a page that scrolls as a whole, and ItemTouchHelper's own edge scrolling only ever scrolls the list it drags in,
 * which has nowhere to go. This scrolls the page instead, a step each frame while the finger is held near an edge, larger the nearer the edge. The list then
 * moves under a finger that may be resting, and ItemTouchHelper only learns where the finger is from touch events: so each step hands the list a move event at
 * the finger's new place on it, which keeps the row under the finger and lets it pass the rows it should.
 */
// The touch listener only watches and never consumes, so it takes no click from anyone: the rows keep their own clicks and accessibility actions.
@SuppressLint("ClickableViewAccessibility")
final class DragEdgeScroller implements View.OnTouchListener, Runnable {

	/** How near the page's top or bottom edge the finger has to be for the page to scroll. */
	private static final int EDGE_DP = 48;
	/** The step with the finger right at the edge; it shrinks with the finger's distance into the edge zone. */
	private static final int MAX_STEP_DP = 12;

	private final NestedScrollView page;
	private final RecyclerView list;
	private final float edge;
	private final float maxStep;
	/** Reused for every frame's look at where the page and the list are on screen. */
	private final int[] location = new int[2];
	private boolean dragging;
	private long downTime;
	private float fingerX;
	private float fingerY;

	/**
	 * Start following the touches the list receives.
	 *
	 * @param page the page that scrolls
	 * @param list the list whose rows are dragged
	 */
	DragEdgeScroller(NestedScrollView page, RecyclerView list) {
		this.page = page;
		this.list = list;
		final float density = page.getResources().getDisplayMetrics().density;
		edge = EDGE_DP * density;
		maxStep = MAX_STEP_DP * density;
		list.setOnTouchListener(this);
	}

	/**
	 * Follow a drag that has started, or stop when it has ended.
	 *
	 * @param dragging whether a row is being dragged
	 */
	void setDragging(boolean dragging) {
		this.dragging = dragging;
	}

	/**
	 * Note where the finger is on the list, and book a step for the next frame. Never consumes: ItemTouchHelper needs every event.
	 *
	 * @param view  the list
	 * @param event a touch event the list received, in its own coordinates
	 * @return false, always
	 */
	@Override
	public boolean onTouch(View view, MotionEvent event) {
		downTime = event.getDownTime();
		fingerX = event.getX();
		fingerY = event.getY();
		page.removeCallbacks(this);
		ViewCompat.postOnAnimation(page, this);
		return false;
	}

	/**
	 * One frame's step. The move event it hands the list comes back through {@link #onTouch}, which books the next frame while the finger stays at the edge.
	 * Whether a drag is on is asked here rather than when booking: the finger lifting reaches {@link #onTouch} before ItemTouchHelper ends the drag, so a step
	 * is always booked at the drop, and has to do nothing.
	 */
	@Override
	public void run() {
		final int step = dragging ? step() : 0;
		if (step == 0) {
			return;
		}
		final int before = page.getScrollY();
		page.scrollBy(0, step);
		final int scrolled = page.getScrollY() - before;
		if (scrolled == 0) {
			return;
		}
		final MotionEvent move = MotionEvent.obtain(
				downTime,
				SystemClock.uptimeMillis(),
				MotionEvent.ACTION_MOVE,
				fingerX,
				fingerY + scrolled,
				0);
		list.dispatchTouchEvent(move);
		move.recycle();
	}

	/** How far to scroll this frame: toward the edge the finger is in, larger the deeper in; 0 outside both zones, or when the page can go no further. */
	private int step() {
		list.getLocationInWindow(location);
		final float finger = location[1] + fingerY;
		page.getLocationInWindow(location);
		final float intoTop = location[1] + edge - finger;
		final float intoBottom = finger - (location[1] + page.getHeight() - edge);
		final int direction = intoTop > 0 ? -1 : intoBottom > 0 ? 1 : 0;
		if (direction == 0 || !page.canScrollVertically(direction)) {
			return 0;
		}
		final float depth = Math.min(edge, direction < 0 ? intoTop : intoBottom);
		return direction * Math.max(1, Math.round(maxStep * depth / edge));
	}
}
