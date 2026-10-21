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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.almothafar.simplebatterynotifier.util.BidiText.isolate;
import static java.util.Objects.isNull;

/**
 * The notification-details picker (#357, #358): which families the status notification's collapsed line shows, up to {@link DetailFamily#MAX_SELECTED}, none
 * allowed, and in what order. Each row carries its family's live value, and a preview on top shows the notification the choice makes, both kept current from
 * the battery broadcasts while the screen is in front.
 * <p>
 * The chosen rows sit above a divider in their order, the rest below it in registry order. A row's handle drags it, across the divider too, which ticks or
 * unticks it; TalkBack users reorder with Move up and Move down instead, and a family ticked by a tap joins the end. Reset in the toolbar puts the default
 * back.
 * <p>
 * Each change is saved at once, so a rotation or a reclaimed process loses nothing, and re-posts the status notification, which no preference change reaches
 * before the next battery broadcast; leaving re-posts it once more.
 */
public class NotificationDetailsFragment extends Fragment {

	/** The families in registry order — cached because {@code values()} hands back a fresh copy on every call. */
	private static final DetailFamily[] FAMILIES = DetailFamily.values();
	/** Rebind payload for a row whose live value is all that moved: its name, box and enabled state stay as they are. */
	private static final Object LIVE_VALUE = new Object();
	private static final int VIEW_TYPE_ROW = 0;
	private static final int VIEW_TYPE_DIVIDER = 1;
	/** The divider's key in {@link #rowKeys}, beside the families' ordinals. */
	private static final int DIVIDER_KEY = -1;

	/** The chosen families, in the order the line shows them: the rows above the divider. */
	private final List<DetailFamily> chosen = new ArrayList<>(DetailFamily.MAX_SELECTED);
	/** The other families, below the divider, in registry order — except mid-drag, when the dragged row may be passing through them. */
	private final List<DetailFamily> unchosen = new ArrayList<>(FAMILIES.length);
	private final RowAdapter adapter = new RowAdapter();
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
	private NotificationService.LiveStatus live;
	private TextView header;
	private View preview;
	private TextView previewTitle;
	private TextView previewLine;
	/** Whether a change was saved since this visit began. Each one re-posts the status notification, and leaving owes it one more. */
	private boolean savedThisVisit;

	/**
	 * Inflate the screen: the preview, then a header line over a card of rows.
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
	 * Load the saved choice and wire the rows to it.
	 *
	 * @param view               the screen inflated by {@link #onCreateView}
	 * @param savedInstanceState unused; every tap is already saved
	 */
	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		final Context context = requireContext();
		setChoice(AppPrefs.notificationDetails(context));
		header = view.findViewById(R.id.notification_details_header);
		preview = view.findViewById(R.id.notification_details_preview);
		previewTitle = view.findViewById(R.id.notification_details_preview_title);
		previewLine = view.findViewById(R.id.notification_details_preview_line);
		describePreview(preview);
		final RecyclerView list = view.findViewById(R.id.notification_details_list);
		list.setLayoutManager(new LinearLayoutManager(context));
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
		requireActivity().setTitle(R.string.notification_details_title);
		savedThisVisit = false;
		// A protected system broadcast, so it takes no export flag — the monitoring service registers for it the same way. Registering returns the current
		// sticky reading, which shows the values without asking the system for it a second time.
		final Intent battery = context.registerReceiver(batteryChanges, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		live = NotificationService.liveStatus(context, battery);
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
	 * The General screen's summary for a choice: the family names in order, e.g. "Speed · Current · Time".
	 *
	 * @param context  context to resolve the names against
	 * @param families the chosen families, in display order
	 * @return the summary line
	 */
	static String summaryOf(Context context, List<DetailFamily> families) {
		if (families.isEmpty()) {
			return context.getString(R.string.notification_details_summary_none);
		}
		return families.stream().map(family -> context.getString(labelRes(family))).collect(Collectors.joining(OngoingStatusContent.DETAIL_SEPARATOR));
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
	 * The name a family goes by, in the picker and in the General screen's summary. Where the details table already names the same reading, this is its name,
	 * so a family reads the same in the picker, the table and the pull-down.
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
		applyChoice(choiceAfterTap(chosen, family));
	}

	/** Show and save a new choice, from a tap or from Reset. */
	private void applyChoice(List<DetailFamily> next) {
		if (next.equals(chosen)) {
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
		chosen.add(to, chosen.remove(from));
		adapter.notifyItemMoved(from, to);
		saveChoice();
	}

	/**
	 * Move a dragged row to where the finger has taken it, without saving: the drop does that. The divider counts as a place, so a row dragged across it
	 * changes sides — ticked when it rises above, unticked when it sinks below.
	 *
	 * @param from the dragged row's place
	 * @param to   the place of the row or divider it passed
	 */
	private void moveRow(int from, int to) {
		final DetailFamily family = familyAt(from);
		final boolean wasChosen = isChosenPlace(from);
		// Taking a chosen row out moves the divider up a place, so landing on the divider's old place puts the row just below it.
		final boolean nowChosen = wasChosen ? to < chosen.size() : to <= chosen.size();
		(wasChosen ? chosen : unchosen).remove(family);
		if (nowChosen) {
			chosen.add(to, family);
		} else {
			// Counted against the divider as it stands now, after the row left its side.
			unchosen.add(unchosenIndex(to), family);
		}
		adapter.notifyItemMoved(from, to);
		// The preview follows the drag, so the line can be judged before letting go.
		showPreview();
	}

	/**
	 * A drag has ended: put the rows below the divider back in registry order, since only the chosen ones keep an order, and save if the choice changed.
	 *
	 * @param before the choice when the drag began
	 */
	private void finishDrag(List<DetailFamily> before) {
		final List<DetailFamily> after = List.copyOf(chosen);
		showChoice(after);
		if (after.equals(before)) {
			return;
		}
		saveChoice();
	}

	/**
	 * Whether a row can trade places with its neighbour: both have to be chosen. Asking about the row's own place also turns away a row between binds, whose
	 * place reads as {@link RecyclerView#NO_POSITION}.
	 */
	private boolean canMove(int position, int step) {
		return isChosenPlace(position) && isChosenPlace(position + step);
	}

	/** Whether a place in the list holds a chosen row. */
	private boolean isChosenPlace(int position) {
		return position >= 0 && position < chosen.size();
	}

	/** Save the choice the rows show and re-post the status notification from it: the one path a tap, a move and a drop all end on. */
	private void saveChoice() {
		final Context context = requireContext();
		AppPrefs.setNotificationDetails(context, chosen);
		render();
		// Reset greys out once the choice is the default again, and comes back as soon as it is not.
		requireActivity().invalidateMenu();
		// Now rather than on the way out: pulling the shade down over this screen doesn't pause it.
		refreshNotification(context);
		savedThisVisit = true;
	}

	/** Chosen families above the divider in their order, the rest below it in registry order. */
	private void setChoice(List<DetailFamily> choice) {
		chosen.clear();
		chosen.addAll(choice);
		unchosen.clear();
		Arrays.stream(FAMILIES).filter(family -> !choice.contains(family)).forEach(unchosen::add);
	}

	/**
	 * Move the rows into the order a new choice gives them, one {@code notifyItemMoved} per row that changes place. A moved row keeps its view, so TalkBack's
	 * focus stays on the row just tapped while it travels to its new place.
	 */
	private void showChoice(List<DetailFamily> choice) {
		final List<Integer> shown = rowKeys();
		setChoice(choice);
		final List<Integer> wanted = rowKeys();
		for (int to = 0; to < wanted.size(); to++) {
			final int from = shown.indexOf(wanted.get(to));
			if (from != to) {
				shown.add(to, shown.remove(from));
				adapter.notifyItemMoved(from, to);
			}
		}
	}

	/** Each row's key from the top: a family's ordinal, or {@link #DIVIDER_KEY}. */
	private List<Integer> rowKeys() {
		final List<Integer> keys = new ArrayList<>(adapter.getItemCount());
		chosen.forEach(family -> keys.add(family.ordinal()));
		keys.add(DIVIDER_KEY);
		unchosen.forEach(family -> keys.add(family.ordinal()));
		return keys;
	}

	/** The family on a row, by its place in the list. */
	private DetailFamily familyAt(int position) {
		return isChosenPlace(position) ? chosen.get(position) : unchosen.get(unchosenIndex(position));
	}

	/** Where a place below the divider falls in {@link #unchosen}. */
	private int unchosenIndex(int position) {
		return position - chosen.size() - 1;
	}

	/** A family's place in the list, past the divider when it is not chosen. */
	private int positionOf(DetailFamily family) {
		final int place = chosen.indexOf(family);
		return place >= 0 ? place : chosen.size() + 1 + unchosen.indexOf(family);
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
		final NotificationService.LiveStatus shown = live;
		live = next;
		for (final DetailFamily family : FAMILIES) {
			if (!Objects.equals(segmentOf(shown, family), segmentOf(next, family))) {
				adapter.notifyItemChanged(positionOf(family), LIVE_VALUE);
			}
		}
		showPreview();
	}

	/** A family's live value; null when it has nothing to show yet, or there is no reading. */
	private String segmentOf(DetailFamily family) {
		return segmentOf(live, family);
	}

	private static String segmentOf(NotificationService.LiveStatus status, DetailFamily family) {
		return isNull(status) ? null : status.segments().get(family);
	}

	/** Show the notification the chosen rows make, in their order: the title, and the line under it unless the choice is empty. */
	private void showPreview() {
		if (isNull(live)) {
			// Nothing to picture without a reading, and an empty title would read as a broken notification.
			preview.setVisibility(View.GONE);
			return;
		}
		preview.setVisibility(View.VISIBLE);
		previewTitle.setText(live.title());
		final String line = OngoingStatusContent.detailLine(chosen, this::segmentOf);
		previewLine.setText(line);
		previewLine.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
	}

	/** TalkBack reads the preview as one item, then says what it is: its two lines alone would sound like a notification has arrived. */
	private void describePreview(View card) {
		ViewCompat.setScreenReaderFocusable(card, true);
		ViewCompat.setAccessibilityDelegate(card, new AccessibilityDelegateCompat() {
			@Override
			public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
				super.onInitializeAccessibilityNodeInfo(host, info);
				info.setRoleDescription(getString(R.string.notification_details_preview));
			}
		});
	}

	/** Refresh the header, the preview and every row: one tap can enable or disable all the unchecked rows at once. */
	private void render() {
		if (chosen.isEmpty()) {
			header.setText(R.string.notification_details_header_empty);
		} else {
			// Formatted rather than passed as ints: getString formats with the configuration locale, which prints ٣ on ar-EG (#96).
			header.setText(getString(R.string.notification_details_header,
					isolate(String.valueOf(DetailFamily.MAX_SELECTED)),
					isolate(String.valueOf(chosen.size()))));
		}
		showPreview();
		adapter.notifyItemRangeChanged(0, adapter.getItemCount());
	}

	/** The chosen families, the divider, then the rest, bound against the fragment's choice and live values. */
	private final class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

		/**
		 * The divider sits right after the chosen rows.
		 *
		 * @param position a place in the list
		 * @return {@link #VIEW_TYPE_DIVIDER} for the divider, {@link #VIEW_TYPE_ROW} for a family
		 */
		@Override
		public int getItemViewType(int position) {
			return position == chosen.size() ? VIEW_TYPE_DIVIDER : VIEW_TYPE_ROW;
		}

		/**
		 * Inflate a family's row or the divider.
		 *
		 * @param parent   the list
		 * @param viewType {@link #VIEW_TYPE_ROW} or {@link #VIEW_TYPE_DIVIDER}
		 * @return the new row
		 */
		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			if (viewType == VIEW_TYPE_DIVIDER) {
				return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_notification_detail_divider, parent, false)) { };
			}
			return new RowHolder(inflater.inflate(R.layout.item_notification_detail, parent, false));
		}

		/**
		 * Bind a family's name, live value, checked state and cap state, and what a tap on it does. The divider has nothing to bind.
		 *
		 * @param holder   the row to bind
		 * @param position the row's place in the list
		 */
		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			if (holder instanceof final RowHolder row) {
				bindRow(row, familyAt(position));
			}
		}

		/**
		 * Bind a row, or only move its live value when that is all that changed ({@link #LIVE_VALUE}).
		 *
		 * @param holder   the row to bind
		 * @param position the row's place in the list
		 * @param payloads {@link #LIVE_VALUE} for each value-only change since the last bind; empty when the row needs binding in full
		 */
		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
			if (payloads.isEmpty()) {
				onBindViewHolder(holder, position);
			} else if (holder instanceof final RowHolder row) {
				row.showValue(segmentOf(familyAt(position)));
			}
		}

		/** @return one row per family, and the divider */
		@Override
		public int getItemCount() {
			return FAMILIES.length + 1;
		}

		private void bindRow(RowHolder holder, DetailFamily family) {
			final boolean checked = chosen.contains(family);
			holder.bind(getString(labelRes(family)), segmentOf(family), checked, checked || chosen.size() < DetailFamily.MAX_SELECTED);
			holder.itemView.setOnClickListener(row -> {
				applyTap(family);
				// Rebinding waits for the next layout pass; the click's accessibility event goes out before it, so the row has to say its new state now.
				holder.checkBox.setChecked(chosen.contains(family));
			});
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
			menu.findItem(R.id.action_reset_notification_details).setEnabled(!chosen.equals(AppPrefs.DEFAULT_NOTIFICATION_DETAILS));
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

		/** The choice when the current drag began, to tell at the drop whether it changed. */
		private List<DetailFamily> before = List.of();

		/**
		 * Rows drag up and down; the divider stays put.
		 *
		 * @param recyclerView the list
		 * @param viewHolder   the row asked about
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
		 * Note the choice as a drag begins.
		 *
		 * @param viewHolder  the row picked up, or null when a drag or swipe ends
		 * @param actionState what the helper is now doing
		 */
		@Override
		public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
			super.onSelectedChanged(viewHolder, actionState);
			if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
				before = List.copyOf(chosen);
			}
		}

		/**
		 * The dropped row has settled into its place: the drag is over.
		 *
		 * @param recyclerView the list
		 * @param viewHolder   the row that was dragged
		 */
		@Override
		public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
			super.clearView(recyclerView, viewHolder);
			finishDrag(before);
		}
	}

	/**
	 * One checkable row. The row is the control, so TalkBack reads "Speed, 9%/h, checked" once rather than a row and then a separate box. A chosen row also
	 * carries Move up and Move down wherever it has a chosen neighbour to trade places with.
	 */
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
			// Both halves of what TalkBack hears: the node it reads a focused row from, and the click event it announces a tap from.
			ViewCompat.setAccessibilityDelegate(itemView, new AccessibilityDelegateCompat() {
				@Override
				public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
					super.onInitializeAccessibilityNodeInfo(host, info);
					info.setClassName(CheckBox.class.getName());
					info.setCheckable(true);
					info.setChecked(checkBox.isChecked() ? AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE : AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE);
					// Asked for each time TalkBack reads the row rather than set when it is bound: a move shifts its neighbours without rebinding them.
					final int position = getBindingAdapterPosition();
					if (canMove(position, -1)) {
						final String moveUp = getString(R.string.notification_details_move_up);
						info.addAction(new AccessibilityActionCompat(R.id.notification_details_action_move_up, moveUp));
					}
					if (canMove(position, 1)) {
						final String moveDown = getString(R.string.notification_details_move_down);
						info.addAction(new AccessibilityActionCompat(R.id.notification_details_action_move_down, moveDown));
					}
				}

				@Override
				public void onInitializeAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event) {
					super.onInitializeAccessibilityEvent(host, event);
					event.setClassName(CheckBox.class.getName());
					event.setChecked(checkBox.isChecked());
				}

				@Override
				public boolean performAccessibilityAction(@NonNull View host, int action, Bundle arguments) {
					final int position = getBindingAdapterPosition();
					final int step = action == R.id.notification_details_action_move_up ? -1 : action == R.id.notification_details_action_move_down ? 1 : 0;
					if (step != 0 && canMove(position, step)) {
						moveChosen(position, position + step);
						return true;
					}
					return super.performAccessibilityAction(host, action, arguments);
				}
			});
		}

		void bind(String labelText, String valueText, boolean checked, boolean enabled) {
			label.setText(labelText);
			showValue(valueText);
			checkBox.setChecked(checked);
			itemView.setEnabled(enabled);
			checkBox.setEnabled(enabled);
			label.setEnabled(enabled);
			value.setEnabled(enabled);
			dragHandle.setEnabled(enabled);
		}

		void showValue(String valueText) {
			value.setText(isNull(valueText) ? "" : valueText);
		}
	}
}
