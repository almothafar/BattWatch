package com.almothafar.simplebatterynotifier.ui.fragment;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.DetailFamily;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.service.OngoingStatusContent;
import com.almothafar.simplebatterynotifier.util.AppPrefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.almothafar.simplebatterynotifier.util.BidiText.isolate;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * The notification-details picker (#357, #358): which families the status notification's collapsed line shows, up to {@link DetailFamily#MAX_SELECTED}, none
 * allowed, and in what order. Each row carries its family's live value, and a preview on top shows the notification the choice makes, both kept current from
 * the battery broadcasts while the screen is in front. A value still on its way reads "Calculating…", and one that is not coming "—": the column is where a
 * phone that never reports a current shows it (#359). Settings → Status Notification opens it directly, so it is the status notification's screen (#384).
 * <p>
 * The chosen rows sit above a divider in their order, the rest below it in registry order. A row's handle drags it, across the divider too, which ticks or
 * unticks it; TalkBack users reorder with Move up and Move down instead, and a family ticked by a tap joins the end. Reset in the toolbar puts the default
 * back. The whole screen is one list, laid out by {@link PickerRows}, so a row dragged to the screen's edge scrolls it along.
 * <p>
 * Each change is saved at once, so a rotation or a reclaimed process loses nothing, and re-posts the status notification, which no preference change reaches
 * before the next battery broadcast; leaving re-posts it once more.
 */
public class NotificationDetailsFragment extends Fragment {

	/** Rebind payload for a row whose live value is all that moved: its name, box and enabled state stay as they are. */
	private static final Object LIVE_VALUE = new Object();
	private static final int VIEW_TYPE_ROW = 0;
	private static final int VIEW_TYPE_DIVIDER = 1;
	private static final int VIEW_TYPE_PREVIEW = 2;
	private static final int VIEW_TYPE_HEADER = 3;

	private final PickerRows rows = new PickerRows();
	private final PickerAdapter adapter = new PickerAdapter();
	private final ItemTouchHelper dragHelper = new ItemTouchHelper(new DragCallback());
	/** Moves the live values with each battery broadcast while the screen is in front, reading them from the broadcast itself (#159). */
	private final BroadcastReceiver batteryChanges = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Registering hands back this same sticky reading, and onResume has already shown it.
			if (isInitialStickyBroadcast()) {
				return;
			}
			showLive(NotificationService.liveStatus(context, intent));
		}
	};
	/** The latest battery reading as the notification renders it; null before the first, or when the system has none. */
	private NotificationService.LiveStatus liveStatus;
	private RecyclerView list;
	/** Whether a change was saved since this visit began. Each one re-posts the status notification, and leaving owes it one more. */
	private boolean savedThisVisit;

	/**
	 * Inflate the screen: one list, which {@link #onViewCreated} fills.
	 *
	 * @param inflater           the inflater to use
	 * @param container          the parent the screen will be attached to
	 * @param savedInstanceState unused; the choice is read back from preferences instead
	 * @return the screen's root view
	 */
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_notification_details, container, false);
	}

	/**
	 * Load the saved choice and wire the list to it.
	 *
	 * @param view               the screen inflated by {@link #onCreateView}
	 * @param savedInstanceState unused; every tap is already saved
	 */
	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		final Context context = requireContext();
		rows.set(AppPrefs.notificationDetails(context));
		list = view.findViewById(R.id.notification_details_list);
		list.setLayoutManager(new LinearLayoutManager(context));
		list.addItemDecoration(new RowCardDecoration(context, PickerRows.FIRST_ROW));
		list.setAdapter(adapter);
		dragHelper.attachToRecyclerView(list);
		// Tied to this screen's view, so Reset leaves the toolbar with it.
		requireActivity().addMenuProvider(new ResetMenu(), getViewLifecycleOwner(), Lifecycle.State.RESUMED);
		if (list.getItemAnimator() instanceof final SimpleItemAnimator animator) {
			// A tap rebinds every row, since it can enable or disable all the unchecked ones. A change animation would swap a fresh view into each row and fade
			// the old one out, and TalkBack's focus would leave with the old view; rebinding in place keeps it on the row just tapped.
			animator.setSupportsChangeAnimations(false);
		}
	}

	/** Title the screen, show the live values, and start following the battery so they stay current while the screen is in front. */
	@Override
	public void onResume() {
		super.onResume();
		final Context context = requireContext();
		requireActivity().setTitle(R.string.pref_header_status_notification);
		savedThisVisit = false;
		// A protected system broadcast, so it takes no export flag — the monitoring service registers for it the same way. Registering returns the current
		// sticky reading, which shows the values without asking the system for it a second time.
		final Intent battery = context.registerReceiver(batteryChanges, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		liveStatus = NotificationService.liveStatus(context, battery);
		render();
	}

	/** Stop following the battery, and re-post the status notification once more if a change was saved during this visit. */
	@Override
	public void onPause() {
		super.onPause();
		final Context context = requireContext();
		context.unregisterReceiver(batteryChanges);
		if (savedThisVisit) {
			// The system drops a notification update that follows the last one too closely, so a fast run of changes can leave the shade on a choice from the
			// middle of the run.
			refreshNotification(context);
		}
	}

	/**
	 * The choice after tapping a family: a chosen one is dropped, any other joins the end, so ticking never undoes the order the others were put in. A tap past
	 * the cap changes nothing — the row is disabled by then, but {@code performClick} and accessibility services can still reach it.
	 *
	 * @param selection the current choice
	 * @param family    the family tapped
	 * @return the new choice
	 */
	static List<DetailFamily> choiceAfterTap(List<DetailFamily> selection, DetailFamily family) {
		if (selection.contains(family)) {
			// collect(toList()) rather than Stream.toList(), which Android only has from API 34.
			return selection.stream().filter(chosen -> chosen != family).collect(Collectors.toList());
		}
		if (selection.size() >= DetailFamily.MAX_SELECTED) {
			return selection;
		}
		final List<DetailFamily> next = new ArrayList<>(selection);
		next.add(family);
		return next;
	}

	/**
	 * The name a family goes by in the picker. Where the details table already names the same reading, this is its name, so a family reads the same in the
	 * picker, the table and the pull-down.
	 *
	 * @param family a family
	 * @return its name's string resource
	 */
	@StringRes
	static int labelRes(DetailFamily family) {
		return switch (family) {
			case SPEED -> R.string.detail_family_speed;
			case CURRENT -> R.string.battery_current;
			case TIME -> R.string.detail_family_time;
			case TEMPERATURE -> R.string.temperature;
			case POWER_SOURCE -> R.string.power_source;
		};
	}

	private void applyTap(DetailFamily family) {
		applyChoice(choiceAfterTap(rows.chosen(), family));
	}

	/** Show and save a new choice, from a tap or from Reset. */
	private void applyChoice(List<DetailFamily> next) {
		if (next.equals(rows.chosen())) {
			return;
		}
		showChoice(next);
		saveChoice();
	}

	/**
	 * Move a chosen row one place up or down, TalkBack's way to reorder (#358).
	 *
	 * @param from the row's place
	 * @param to   the place next to it, both among the chosen rows
	 */
	private void moveChosen(int from, int to) {
		rows.move(from, to);
		adapter.notifyItemMoved(from, to);
		saveChoice();
	}

	/**
	 * Move a dragged row to where the finger has taken it, without saving: the drop does that.
	 *
	 * @param from the dragged row's place
	 * @param to   the place of the row or divider it passed
	 */
	private void moveRow(int from, int to) {
		final boolean crossed = rows.move(from, to);
		adapter.notifyItemMoved(from, to);
		if (crossed) {
			// Crossing the line ticks or unticks the row there and then, which can move the count past the cap and grey or light every unchosen row.
			render();
		} else {
			// The preview follows the drag, so the line can be judged before letting go.
			redraw(PickerRows.PREVIEW);
		}
	}

	/** The finger is off a dragged row: save the choice if the drag changed it. Compared with the saved choice, so a drop is never taken for saved. */
	private void saveDrop() {
		if (!rows.chosen().equals(AppPrefs.notificationDetails(requireContext()))) {
			saveChoice();
		}
	}

	/**
	 * Whether a row can trade places with its neighbour: both have to be chosen. Asking about the row's own place also turns away a row between binds, whose
	 * place reads as {@link RecyclerView#NO_POSITION}.
	 */
	private boolean canMove(int position, int step) {
		return rows.isChosenPlace(position) && rows.isChosenPlace(position + step);
	}

	/** Save the choice the rows show and re-post the status notification from it: the one path a tap, a move and a drop all end on. */
	private void saveChoice() {
		final Context context = requireContext();
		AppPrefs.setNotificationDetails(context, rows.chosen());
		render();
		// Reset greys out once the choice is the default again, and comes back as soon as it is not.
		requireActivity().invalidateMenu();
		// Now rather than on the way out: pulling the shade down over this screen doesn't pause it.
		refreshNotification(context);
		savedThisVisit = true;
	}

	/** Move the rows into the order a new choice gives them, each as a move, so it keeps its view: TalkBack's focus stays on a row as it travels. */
	private void showChoice(List<DetailFamily> choice) {
		rows.change(choice, adapter::notifyItemMoved);
	}

	/**
	 * Re-post the status notification from the saved choice. The application context, not this screen's: the monitoring service builds the notification from
	 * its Service context, which shares the application's resources — so the refresh words it as the next battery broadcast will, whatever language this
	 * screen is showing.
	 */
	private static void refreshNotification(Context context) {
		NotificationService.refreshOngoingNotification(context.getApplicationContext());
	}

	/**
	 * Take a new battery reading: redraw the preview, and just the rows whose value moved — the battery broadcast fires every few seconds, and usually moves
	 * a value or two of the five.
	 */
	private void showLive(NotificationService.LiveStatus next) {
		final NotificationService.LiveStatus shown = liveStatus;
		liveStatus = next;
		for (final DetailFamily family : DetailFamily.values()) {
			// The values rather than the segments: a row can turn from "Calculating…" to "—" with no segment either side.
			if (!Objects.equals(rowValue(shown, family), rowValue(next, family))) {
				adapter.notifyItemChanged(rows.positionOf(family), LIVE_VALUE);
			}
		}
		redraw(PickerRows.PREVIEW);
	}

	/** What TalkBack reads as a chosen row's state, "Checked, 2 of 3"; null for an unchosen row, which keeps TalkBack's own "not checked". */
	private String stateDescriptionOf(DetailFamily family) {
		final List<DetailFamily> chosen = rows.chosen();
		final int place = chosen.indexOf(family);
		if (place < 0) {
			return null;
		}
		// Formatted rather than passed as ints, as the header is: getString formats with the configuration locale, which prints ٢ on ar-EG (#96).
		return getString(R.string.notification_details_place, isolate(String.valueOf(place + 1)), isolate(String.valueOf(chosen.size())));
	}

	/** A family's segment of the line; null when it has nothing to show yet, or there is no reading. */
	private String segmentOf(DetailFamily family) {
		return segmentOf(liveStatus, family);
	}

	private static String segmentOf(NotificationService.LiveStatus status, DetailFamily family) {
		return isNull(status) ? null : status.segments().get(family);
	}

	/** What a family's row shows: its segment, else "—" when none is coming or "Calculating…" while one is on its way (#359); null when there is no reading. */
	private String rowValue(DetailFamily family) {
		return rowValue(liveStatus, family);
	}

	private String rowValue(NotificationService.LiveStatus status, DetailFamily family) {
		final String segment = segmentOf(status, family);
		if (isNull(status) || nonNull(segment)) {
			return segment;
		}
		// battery_value_pending is the details table's "—" for a value that is not coming (#216); "Calculating…" is for one on its way.
		return getString(status.unavailable().contains(family) ? R.string.battery_value_pending : R.string.notification_status_calculating);
	}

	/** Refresh the header, the preview and every row: one tap can enable or disable all the unchecked rows at once. */
	private void render() {
		redraw(PickerRows.HEADER);
		redraw(PickerRows.PREVIEW);
		adapter.notifyItemRangeChanged(PickerRows.FIRST_ROW, rows.size() - PickerRows.FIRST_ROW);
	}

	/** Redraw the preview or the header in place, if it is in view; one out of view is drawn as it comes back (see {@link SummaryHolder}). */
	private void redraw(int position) {
		if (list.findViewHolderForAdapterPosition(position) instanceof final SummaryHolder summary) {
			summary.show();
		}
	}

	/** The preview, the header, the chosen families, the divider, then the rest, bound against the fragment's choice and live values. */
	private final class PickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

		/**
		 * What sits at a place: the preview and the header at the top, the divider right after the chosen rows, a family's row anywhere else.
		 *
		 * @param position a place in the list
		 * @return one of the {@code VIEW_TYPE_} constants
		 */
		@Override
		public int getItemViewType(int position) {
			return switch (position) {
				case PickerRows.PREVIEW -> VIEW_TYPE_PREVIEW;
				case PickerRows.HEADER -> VIEW_TYPE_HEADER;
				default -> position == rows.divider() ? VIEW_TYPE_DIVIDER : VIEW_TYPE_ROW;
			};
		}

		/**
		 * Inflate the preview, the header, the divider or a family's row.
		 *
		 * @param parent   the list
		 * @param viewType one of the {@code VIEW_TYPE_} constants
		 * @return the new item
		 */
		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			return switch (viewType) {
				case VIEW_TYPE_PREVIEW -> new PreviewHolder(inflater.inflate(R.layout.item_notification_detail_preview, parent, false));
				case VIEW_TYPE_HEADER -> new HeaderHolder(inflater.inflate(R.layout.item_notification_detail_header, parent, false));
				case VIEW_TYPE_DIVIDER -> new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_notification_detail_divider, parent, false)) { };
				default -> new RowHolder(inflater.inflate(R.layout.item_notification_detail, parent, false));
			};
		}

		/**
		 * Bind a family's name, live value, checked state and cap state, and what a tap on it does. The divider has nothing to bind, and the preview and the
		 * header draw themselves as they come into view ({@link #onViewAttachedToWindow}).
		 *
		 * @param holder   the item to bind
		 * @param position its place in the list
		 */
		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			if (holder instanceof final RowHolder row) {
				bindRow(row, rows.familyAt(position));
			}
		}

		/**
		 * Bind a row, or only move its live value when that is all that changed ({@link #LIVE_VALUE}).
		 *
		 * @param holder   the item to bind
		 * @param position its place in the list
		 * @param payloads {@link #LIVE_VALUE} for each value-only change since the last bind; empty when the row needs binding in full
		 */
		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
			if (payloads.isEmpty()) {
				onBindViewHolder(holder, position);
			} else if (holder instanceof final RowHolder row) {
				row.showValue(rowValue(rows.familyAt(position)));
			}
		}

		/**
		 * Draw the preview or the header as it comes into view. They are drawn here and redrawn in place rather than bound like the rows: a rebind would
		 * animate the rows under them up or down whenever their height changed, away from the card painted behind the rows.
		 *
		 * @param holder the item coming into view
		 */
		@Override
		public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
			if (holder instanceof final SummaryHolder summary) {
				summary.show();
			}
		}

		/** @return the preview, the header, one row per family, and the divider */
		@Override
		public int getItemCount() {
			return rows.size();
		}

		private void bindRow(RowHolder holder, DetailFamily family) {
			final List<DetailFamily> chosen = rows.chosen();
			holder.bind(getString(labelRes(family)), rowValue(family), chosen.contains(family) || chosen.size() < DetailFamily.MAX_SELECTED);
			showTick(holder, family);
			holder.itemView.setOnClickListener(row -> {
				applyTap(family);
				// Rebinding waits for the next layout pass; the click's accessibility event goes out before it, so the row has to say its new state now.
				showTick(holder, family);
			});
		}

		/**
		 * Tick or untick a row's box, and give it its place as the state TalkBack reads. Set on the view rather than reported when TalkBack asks: a change on
		 * the focused row is announced, which is how a Move up is heard.
		 */
		private void showTick(RowHolder holder, DetailFamily family) {
			holder.checkBox.setChecked(rows.chosen().contains(family));
			ViewCompat.setStateDescription(holder.itemView, stateDescriptionOf(family));
		}
	}

	/** The toolbar's Reset, which puts back the choice a new install starts with. Greyed out while that is the choice already. */
	private final class ResetMenu implements MenuProvider {

		/**
		 * Add Reset to the toolbar.
		 *
		 * @param menu     the toolbar's menu
		 * @param inflater the inflater to use
		 */
		@Override
		public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
			inflater.inflate(R.menu.menu_notification_details, menu);
		}

		/**
		 * Grey Reset out while it would change nothing.
		 *
		 * @param menu the toolbar's menu
		 */
		@Override
		public void onPrepareMenu(@NonNull Menu menu) {
			menu.findItem(R.id.action_reset_notification_details).setEnabled(!rows.chosen().equals(AppPrefs.DEFAULT_NOTIFICATION_DETAILS));
		}

		/**
		 * Put the default choice back.
		 *
		 * @param item the item tapped
		 * @return true when it was Reset
		 */
		@Override
		public boolean onMenuItemSelected(@NonNull MenuItem item) {
			if (item.getItemId() != R.id.action_reset_notification_details) {
				return false;
			}
			applyChoice(AppPrefs.DEFAULT_NOTIFICATION_DETAILS);
			return true;
		}
	}

	/**
	 * Drags a row by its handle, up or down, across the divider too. A drag starts only from an enabled handle, so a row that is disabled — unchosen, with
	 * the cap already reached — does not move, and a drag can never put a fourth family above the divider.
	 */
	private final class DragCallback extends ItemTouchHelper.Callback {

		/**
		 * Rows drag up and down; the preview, the header and the divider stay put.
		 *
		 * @param recyclerView the list
		 * @param viewHolder   the item asked about
		 * @return the directions it may be dragged in
		 */
		@Override
		public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
			return viewHolder instanceof RowHolder ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
		}

		/** @return false: a drag starts from the handle, so a long press on the row stays a press */
		@Override
		public boolean isLongPressDragEnabled() {
			return false;
		}

		/**
		 * Look for a place to move into on every move. A row takes another row's place, or the divider's, only once it is past that item's far edge, which is
		 * wait enough. The default first waits for the row to move half its own height, more than the divider's whole height: a row dragged across the
		 * divider and back to where it began would stay on the wrong side of it.
		 *
		 * @param viewHolder the row being dragged
		 * @return 0, no distance first
		 */
		@Override
		public float getMoveThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
			return 0;
		}

		/**
		 * A row passes the other rows and the divider, never the preview or the header above them.
		 *
		 * @param recyclerView the list
		 * @param current      the row being dragged
		 * @param target       the item it is over
		 * @return whether the row may take the item's place
		 */
		@Override
		public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
			return target.getBindingAdapterPosition() >= PickerRows.FIRST_ROW;
		}

		/**
		 * Follow the finger past another row, or the divider.
		 *
		 * @param recyclerView the list
		 * @param dragged      the row being dragged
		 * @param target       the row or divider it passed
		 * @return true: the move always happens
		 */
		@Override
		public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder dragged, @NonNull RecyclerView.ViewHolder target) {
			moveRow(dragged.getBindingAdapterPosition(), target.getBindingAdapterPosition());
			return true;
		}

		/**
		 * Never called: rows do not swipe.
		 *
		 * @param viewHolder the row
		 * @param direction  the swipe's direction
		 */
		@Override
		public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
			// getMovementFlags gives no swipe directions.
		}

		/**
		 * Lift a row as a drag picks it up, and save the drop as the finger lifts.
		 *
		 * @param viewHolder  the row picked up, or null when a drag ends
		 * @param actionState what the helper is now doing
		 */
		@Override
		public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
			super.onSelectedChanged(viewHolder, actionState);
			if (viewHolder instanceof final RowHolder row) {
				row.lift(true);
			} else {
				// Saved now rather than once the row has glided into place: a touch on the row on its way cuts the glide short, and the screen can be gone by
				// its end, rebuilt when it turned. The touch is cancelled before the screen goes, so this runs while it is still here.
				saveDrop();
			}
		}

		/**
		 * The dropped row has glided into its place: set it back into the card, and put the rows below the divider back in registry order, since only the
		 * chosen ones keep an order.
		 *
		 * @param recyclerView the list
		 * @param viewHolder   the row that was dragged
		 */
		@Override
		public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
			super.clearView(recyclerView, viewHolder);
			((RowHolder) viewHolder).lift(false);
			showChoice(rows.chosen());
		}
	}

	/**
	 * The preview or the header. Each sums up the whole choice rather than showing one family, so it is drawn from the choice as it comes into view and
	 * redrawn in place while there, rather than bound by its place in the list.
	 */
	private abstract static class SummaryHolder extends RecyclerView.ViewHolder {

		SummaryHolder(View itemView) {
			super(itemView);
		}

		/** Draw the choice as it stands. */
		abstract void show();
	}

	/** The status notification as the chosen rows make it (#358): the title, and the line they make in their order. */
	private final class PreviewHolder extends SummaryHolder {

		private final View card;
		private final TextView title;
		private final TextView line;

		PreviewHolder(View itemView) {
			super(itemView);
			card = itemView.findViewById(R.id.notification_details_preview);
			title = itemView.findViewById(R.id.notification_details_preview_title);
			line = itemView.findViewById(R.id.notification_details_preview_line);
			// TalkBack reads the preview as one item, then says what it is: its two lines alone would sound like a notification has arrived.
			ViewCompat.setScreenReaderFocusable(card, true);
			ViewCompat.setAccessibilityDelegate(card, new AccessibilityDelegateCompat() {
				@Override
				public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
					super.onInitializeAccessibilityNodeInfo(host, info);
					info.setRoleDescription(getString(R.string.notification_details_preview));
				}
			});
		}

		/** The title, and the line under it unless the choice is empty. */
		@Override
		void show() {
			if (isNull(liveStatus)) {
				// Nothing to picture without a reading, and an empty title would read as a broken notification.
				card.setVisibility(View.GONE);
				return;
			}
			card.setVisibility(View.VISIBLE);
			title.setText(liveStatus.title());
			final String text = OngoingStatusContent.detailLine(requireContext(),
					rows.chosen(),
					NotificationDetailsFragment.this::segmentOf,
					liveStatus.unavailable()::contains);
			line.setText(text);
			line.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
		}
	}

	/** How many families are chosen, of the most allowed, or what an empty choice means. */
	private final class HeaderHolder extends SummaryHolder {

		private final TextView text;

		HeaderHolder(View itemView) {
			super(itemView);
			text = (TextView) itemView;
		}

		/** The count, written only when it reads differently: the header is a live region, which TalkBack reads out each time it is set. */
		@Override
		void show() {
			final String next = countText();
			// The same words again would be read out again too: after a Move up, over the row's own "Checked, 2 of 3".
			if (!next.contentEquals(text.getText())) {
				text.setText(next);
			}
		}

		private String countText() {
			final int count = rows.chosen().size();
			if (count == 0) {
				return getString(R.string.notification_details_header_empty);
			}
			// Formatted rather than passed as ints: getString formats with the configuration locale, which prints ٣ on ar-EG (#96).
			return getString(R.string.notification_details_header, isolate(String.valueOf(DetailFamily.MAX_SELECTED)), isolate(String.valueOf(count)));
		}
	}

	/** One checkable row. The row is the control, so TalkBack reads "Speed, 9%/h, checked" once rather than a row and then a separate box. */
	private final class RowHolder extends RecyclerView.ViewHolder {

		private final CheckBox checkBox;
		private final TextView label;
		private final TextView value;
		private final View dragHandle;

		// The handle takes no click: TalkBack users reorder through the row's own Move up and Move down, which a click on the handle could not offer.
		@SuppressLint("ClickableViewAccessibility")
		RowHolder(View itemView) {
			super(itemView);
			checkBox = itemView.findViewById(R.id.detail_checkbox);
			label = itemView.findViewById(R.id.detail_label);
			value = itemView.findViewById(R.id.detail_value);
			dragHandle = itemView.findViewById(R.id.detail_drag_handle);
			// Consumed, press and all, so a touch on the handle that never moves does not tick the row as a click would. A disabled view's touch listener is
			// never called, so a handle bind() disabled starts nothing: that is what keeps a fourth family from being dragged in.
			dragHandle.setOnTouchListener((handle, event) -> {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					dragHelper.startDrag(this);
				}
				return true;
			});
			ViewCompat.setAccessibilityDelegate(itemView, new RowAccessibility(this));
		}

		void bind(String labelText, String valueText, boolean enabled) {
			label.setText(labelText);
			showValue(valueText);
			itemView.setEnabled(enabled);
			checkBox.setEnabled(enabled);
			label.setEnabled(enabled);
			value.setEnabled(enabled);
			dragHandle.setEnabled(enabled);
		}

		void showValue(String valueText) {
			value.setText(isNull(valueText) ? "" : valueText);
		}

		/** Lift the row out of its card for a drag, with a card and a shadow of its own so it hides the rows it passes, or set it back down. */
		void lift(boolean lifted) {
			itemView.setBackgroundResource(lifted ? R.drawable.bg_notification_detail_lifted : 0);
			itemView.setTranslationZ(lifted ? itemView.getResources().getDimension(R.dimen.notification_detail_lift) : 0);
		}
	}

	/**
	 * What TalkBack hears from a row, both halves of it: the node it reads a focused row from, and the click event it announces a tap from, each as a
	 * checkbox. A chosen row also carries Move up and Move down wherever it has a chosen neighbour to trade places with.
	 */
	private final class RowAccessibility extends AccessibilityDelegateCompat {

		private final RowHolder row;

		RowAccessibility(RowHolder row) {
			this.row = row;
		}

		/**
		 * Brief TalkBack on the row as a checkbox, with the moves it can make.
		 *
		 * @param host the row
		 * @param info the node TalkBack reads it from
		 */
		@Override
		public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
			super.onInitializeAccessibilityNodeInfo(host, info);
			info.setClassName(CheckBox.class.getName());
			info.setCheckable(true);
			info.setChecked(row.checkBox.isChecked() ? AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE : AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE);
			// Asked for each time TalkBack reads the row rather than set when it is bound: a move shifts its neighbours without rebinding them.
			final int position = row.getBindingAdapterPosition();
			if (canMove(position, -1)) {
				info.addAction(new AccessibilityActionCompat(R.id.notification_details_action_move_up, getString(R.string.notification_details_move_up)));
			}
			if (canMove(position, 1)) {
				info.addAction(new AccessibilityActionCompat(R.id.notification_details_action_move_down, getString(R.string.notification_details_move_down)));
			}
		}

		/**
		 * Brief the row's events as a checkbox's, so a tap is announced with the state it left.
		 *
		 * @param host  the row
		 * @param event the event going out
		 */
		@Override
		public void onInitializeAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event) {
			super.onInitializeAccessibilityEvent(host, event);
			event.setClassName(CheckBox.class.getName());
			event.setChecked(row.checkBox.isChecked());
		}

		/**
		 * Carry out Move up or Move down; any other action is the row's own.
		 *
		 * @param host      the row
		 * @param action    the action TalkBack asked for
		 * @param arguments the action's arguments
		 * @return whether the action was carried out
		 */
		@Override
		public boolean performAccessibilityAction(@NonNull View host, int action, Bundle arguments) {
			final int position = row.getBindingAdapterPosition();
			final int step = action == R.id.notification_details_action_move_up ? -1 : action == R.id.notification_details_action_move_down ? 1 : 0;
			if (step != 0 && canMove(position, step)) {
				moveChosen(position, position + step);
				return true;
			}
			return super.performAccessibilityAction(host, action, arguments);
		}
	}
}
