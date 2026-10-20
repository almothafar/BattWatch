package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.almothafar.simplebatterynotifier.util.BidiText.isolate;
import static java.util.Objects.isNull;

/**
 * The notification-details picker (#357): which families the status notification's collapsed line shows, up to {@link DetailFamily#MAX_SELECTED}, none allowed.
 * Each row carries its family's live value, kept current from the battery broadcasts while the screen is in front.
 * <p>
 * A tap is saved at once, so a rotation or a reclaimed process loses nothing. The status notification is refreshed on the way out, because no preference change
 * reaches it before the next battery broadcast. Rows list in the registry's order and a choice is saved in that order; reordering is #358.
 */
public class NotificationDetailsFragment extends Fragment {

	/** The rows, in registry order — cached because {@code values()} hands back a fresh copy on every call. */
	private static final DetailFamily[] FAMILIES = DetailFamily.values();
	/** Rebind payload for a row whose live value is all that moved: its name, box and enabled state stay as they are. */
	private static final Object LIVE_VALUE = new Object();

	private final List<DetailFamily> selection = new ArrayList<>(DetailFamily.MAX_SELECTED);
	private final RowAdapter adapter = new RowAdapter();
	/** Moves the live values with each battery broadcast while the screen is in front, reading them from the broadcast itself (#159). */
	private final BroadcastReceiver batteryChanges = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Registering hands back this same sticky reading, and onResume has already shown it.
			if (isInitialStickyBroadcast()) {
				return;
			}
			showLiveValues(NotificationService.liveDetailSegments(context, intent));
		}
	};
	private Map<DetailFamily, String> liveValues = new EnumMap<>(DetailFamily.class);
	private TextView header;
	/** What the notification was built from when this visit began, so leaving re-posts it only when that moved. */
	private NotificationSettings settingsAtResume;

	/**
	 * Inflate the screen: a header line over a card of rows.
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
		selection.clear();
		selection.addAll(AppPrefs.notificationDetails(context));
		header = view.findViewById(R.id.notification_details_header);
		final RecyclerView list = view.findViewById(R.id.notification_details_list);
		list.setLayoutManager(new LinearLayoutManager(context));
		list.setAdapter(adapter);
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
		settingsAtResume = NotificationSettings.read(context);
		// A protected system broadcast, so it takes no export flag — the monitoring service registers for it the same way. Registering returns the current
		// sticky reading, which shows the values without asking the system for it a second time.
		final Intent battery = context.registerReceiver(batteryChanges, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		liveValues = NotificationService.liveDetailSegments(context, battery);
		render();
	}

	/** Stop following the battery, and refresh the status notification if this visit changed what it is built from. */
	@Override
	public void onPause() {
		super.onPause();
		final Context context = requireContext();
		context.unregisterReceiver(batteryChanges);
		if (!NotificationSettings.read(context).equals(settingsAtResume)) {
			// The application context, not this screen's: the monitoring service builds the notification from its Service context, which shares the
			// application's resources — so the refresh words it as the next battery broadcast will, whatever language this screen is showing.
			NotificationService.refreshOngoingNotification(context.getApplicationContext());
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
	 * The choice after tapping a family: chosen ones are dropped, others added in the registry's order. A tap past the cap changes nothing — the row is
	 * disabled by then, but {@code performClick} and accessibility services can still reach it.
	 *
	 * @param selection the current choice
	 * @param family    the family tapped
	 * @return the new choice
	 */
	static List<DetailFamily> choiceAfterTap(List<DetailFamily> selection, DetailFamily family) {
		// collect(toList()) rather than Stream.toList(), which Android only has from API 34.
		if (selection.contains(family)) {
			return selection.stream().filter(chosen -> chosen != family).collect(Collectors.toList());
		}
		if (selection.size() >= DetailFamily.MAX_SELECTED) {
			return selection;
		}
		return Arrays.stream(FAMILIES).filter(candidate -> candidate == family || selection.contains(candidate)).collect(Collectors.toList());
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
		final List<DetailFamily> next = choiceAfterTap(selection, family);
		if (next.equals(selection)) {
			return;
		}
		selection.clear();
		selection.addAll(next);
		AppPrefs.setNotificationDetails(requireContext(), next);
		render();
	}

	/** Redraw just the rows whose value moved: the battery broadcast fires every few seconds, and usually moves a value or two of the five. */
	private void showLiveValues(Map<DetailFamily, String> values) {
		final Map<DetailFamily, String> shown = liveValues;
		liveValues = values;
		for (final DetailFamily family : FAMILIES) {
			if (!Objects.equals(shown.get(family), values.get(family))) {
				adapter.notifyItemChanged(family.ordinal(), LIVE_VALUE);
			}
		}
	}

	/** Refresh the header and every row: one tap can enable or disable all the unchecked rows at once. */
	private void render() {
		if (selection.isEmpty()) {
			header.setText(R.string.notification_details_header_empty);
		} else {
			// Formatted rather than passed as ints: getString formats with the configuration locale, which prints ٣ on ar-EG (#96).
			header.setText(getString(R.string.notification_details_header,
					isolate(String.valueOf(DetailFamily.MAX_SELECTED)),
					isolate(String.valueOf(selection.size()))));
		}
		adapter.notifyItemRangeChanged(0, adapter.getItemCount());
	}

	/** The five families, in registry order, bound against the fragment's choice and live values. */
	private final class RowAdapter extends RecyclerView.Adapter<RowHolder> {

		/**
		 * Inflate one family's row.
		 *
		 * @param parent   the list
		 * @param viewType unused; every row has the same layout
		 * @return the new row
		 */
		@NonNull
		@Override
		public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new RowHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_detail, parent, false));
		}

		/**
		 * Bind a family's name, live value, checked state and cap state, and what a tap on it does.
		 *
		 * @param holder   the row to bind
		 * @param position the family's place in the registry
		 */
		@Override
		public void onBindViewHolder(@NonNull RowHolder holder, int position) {
			final DetailFamily family = FAMILIES[position];
			final boolean checked = selection.contains(family);
			holder.bind(getString(labelRes(family)), liveValues.get(family), checked, checked || selection.size() < DetailFamily.MAX_SELECTED);
			holder.itemView.setOnClickListener(row -> {
				applyTap(family);
				// Rebinding waits for the next layout pass; the click's accessibility event goes out before it, so the row has to say its new state now.
				holder.checkBox.setChecked(selection.contains(family));
			});
		}

		/**
		 * Bind a row, or only move its live value when that is all that changed ({@link #LIVE_VALUE}).
		 *
		 * @param holder   the row to bind
		 * @param position the family's place in the registry
		 * @param payloads {@link #LIVE_VALUE} for each value-only change since the last bind; empty when the row needs binding in full
		 */
		@Override
		public void onBindViewHolder(@NonNull RowHolder holder, int position, @NonNull List<Object> payloads) {
			if (payloads.isEmpty()) {
				onBindViewHolder(holder, position);
			} else {
				holder.showValue(liveValues.get(FAMILIES[position]));
			}
		}

		/** @return one row per family */
		@Override
		public int getItemCount() {
			return FAMILIES.length;
		}
	}

	/** One checkable row. The row is the control, so TalkBack reads "Speed, 9%/h, checked" once rather than a row and then a separate box. */
	private static final class RowHolder extends RecyclerView.ViewHolder {

		private final CheckBox checkBox;
		private final TextView label;
		private final TextView value;

		RowHolder(View itemView) {
			super(itemView);
			checkBox = itemView.findViewById(R.id.detail_checkbox);
			label = itemView.findViewById(R.id.detail_label);
			value = itemView.findViewById(R.id.detail_value);
			// Both halves of what TalkBack hears: the node it reads a focused row from, and the click event it announces a tap from.
			ViewCompat.setAccessibilityDelegate(itemView, new AccessibilityDelegateCompat() {
				@Override
				public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
					super.onInitializeAccessibilityNodeInfo(host, info);
					info.setClassName(CheckBox.class.getName());
					info.setCheckable(true);
					info.setChecked(checkBox.isChecked() ? AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE : AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE);
				}

				@Override
				public void onInitializeAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event) {
					super.onInitializeAccessibilityEvent(host, event);
					event.setClassName(CheckBox.class.getName());
					event.setChecked(checkBox.isChecked());
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
		}

		void showValue(String valueText) {
			value.setText(isNull(valueText) ? "" : valueText);
		}
	}

	/**
	 * What the status notification is built from that this screen can change: the chosen details, and whether the pull-down still follows the show-rate switch
	 * #357 replaced. The first saved choice retires that switch, so a tap and its undo can leave the details as they were and the pull-down not.
	 *
	 * @param details           the chosen families, in display order
	 * @param pullDownShowsRate whether the pull-down carries its rate and time lines
	 */
	private record NotificationSettings(List<DetailFamily> details, boolean pullDownShowsRate) {

		static NotificationSettings read(Context context) {
			return new NotificationSettings(AppPrefs.notificationDetails(context), AppPrefs.legacyShowRateOn(context));
		}
	}
}
