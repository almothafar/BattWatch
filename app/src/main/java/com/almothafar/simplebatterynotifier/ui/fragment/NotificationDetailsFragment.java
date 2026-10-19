package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

	private final List<DetailFamily> selection = new ArrayList<>(DetailFamily.MAX_SELECTED);
	private final RowAdapter adapter = new RowAdapter();
	/** Re-reads the live values on each battery broadcast while the screen is in front, so they move as the battery does. */
	private final BroadcastReceiver batteryChanges = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			showLiveValues(NotificationService.liveDetailSegments(context));
		}
	};
	private Map<DetailFamily, String> liveValues = new EnumMap<>(DetailFamily.class);
	private TextView header;
	/** Whether this visit changed the choice, so looking without touching doesn't re-post the notification. */
	private boolean changed;

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

	/** Show the live values, and start following the battery so they stay current while the screen is in front. */
	@Override
	public void onResume() {
		super.onResume();
		final Context context = requireContext();
		liveValues = NotificationService.liveDetailSegments(context);
		render();
		// A protected system broadcast, so it takes no export flag — the monitoring service registers for it the same way.
		context.registerReceiver(batteryChanges, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	}

	/** Stop following the battery, and refresh the status notification if this visit changed the choice. */
	@Override
	public void onPause() {
		super.onPause();
		final Context context = requireContext();
		context.unregisterReceiver(batteryChanges);
		if (changed) {
			changed = false;
			// The application context, which the monitoring service builds the notification from — so the refresh can't word it in another language than the
			// next battery broadcast will.
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
	 * The name a family goes by, in the picker and in the General screen's summary.
	 *
	 * @param family a family
	 * @return its name's string resource
	 */
	@StringRes
	static int labelRes(DetailFamily family) {
		return switch (family) {
			case SPEED -> R.string.detail_family_speed;
			case CURRENT -> R.string.detail_family_current;
			case TIME -> R.string.detail_family_time;
			case TEMPERATURE -> R.string.detail_family_temperature;
			case POWER_SOURCE -> R.string.detail_family_power_source;
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
		changed = true;
		render();
	}

	/** Rebind the rows for new live values; skipped when nothing moved, since the battery broadcast fires every few seconds. */
	private void showLiveValues(Map<DetailFamily, String> values) {
		if (values.equals(liveValues)) {
			return;
		}
		liveValues = values;
		adapter.notifyItemRangeChanged(0, adapter.getItemCount());
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
			ViewCompat.setAccessibilityDelegate(itemView, new AccessibilityDelegateCompat() {
				@Override
				public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
					super.onInitializeAccessibilityNodeInfo(host, info);
					info.setClassName(CheckBox.class.getName());
					info.setCheckable(true);
					info.setChecked(checkBox.isChecked());
				}
			});
		}

		void bind(String labelText, String valueText, boolean checked, boolean enabled) {
			label.setText(labelText);
			value.setText(isNull(valueText) ? "" : valueText);
			checkBox.setChecked(checked);
			itemView.setEnabled(enabled);
			checkBox.setEnabled(enabled);
			label.setEnabled(enabled);
			value.setEnabled(enabled);
		}
	}
}
