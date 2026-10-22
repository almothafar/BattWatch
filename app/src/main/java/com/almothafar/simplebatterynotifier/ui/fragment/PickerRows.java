package com.almothafar.simplebatterynotifier.ui.fragment;

import com.almothafar.simplebatterynotifier.model.DetailFamily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The notification-details picker's list from the top (#358): the preview, the header, then a row per family — the chosen ones in their order, a divider, and
 * the rest in registry order. It knows which family sits at each place and moves the families between places, so the arithmetic the divider brings stays in
 * one class rather than wherever the list is touched.
 */
final class PickerRows {

	/** The preview's place, at the top. */
	static final int PREVIEW = 0;
	/** The header's place, under the preview. */
	static final int HEADER = 1;
	/** The first family row's place, under the header. */
	static final int FIRST_ROW = 2;
	/** The families in registry order — cached because {@code values()} hands back a fresh copy on every call. */
	private static final DetailFamily[] FAMILIES = DetailFamily.values();
	/** Stands for the divider beside the families, when working out which rows a new choice moves. */
	private static final Object DIVIDER = new Object();

	/** The chosen families, in the order the line shows them: the rows above the divider. */
	private final List<DetailFamily> chosen = new ArrayList<>(DetailFamily.MAX_SELECTED);
	/** The other families, below the divider, in registry order — except mid-drag, when the dragged row may be passing through them. */
	private final List<DetailFamily> unchosen = new ArrayList<>(FAMILIES.length);

	/** @return how many places the list has: the preview, the header, a row per family and the divider */
	int size() {
		return FIRST_ROW + FAMILIES.length + 1;
	}

	/** @return the chosen families in their order, as they stand now: a copy, which later moves leave alone */
	List<DetailFamily> chosen() {
		return List.copyOf(chosen);
	}

	/** @return the divider's place, right under the chosen rows */
	int divider() {
		return FIRST_ROW + chosen.size();
	}

	/** Whether a place holds a chosen row: not the preview, the header, the divider or a row below it, nor {@code NO_POSITION}. */
	boolean isChosenPlace(int position) {
		return position >= FIRST_ROW && position < divider();
	}

	/** The family on a row, by its place. */
	DetailFamily familyAt(int position) {
		return isChosenPlace(position) ? chosen.get(position - FIRST_ROW) : unchosen.get(position - divider() - 1);
	}

	/** A family's place, past the divider when it is not chosen. */
	int positionOf(DetailFamily family) {
		final int place = chosen.indexOf(family);
		return place >= 0 ? FIRST_ROW + place : divider() + 1 + unchosen.indexOf(family);
	}

	/** Put a choice's families above the divider in its order, and the rest below it in registry order. */
	void set(List<DetailFamily> choice) {
		chosen.clear();
		chosen.addAll(choice);
		unchosen.clear();
		Arrays.stream(FAMILIES).filter(family -> !choice.contains(family)).forEach(unchosen::add);
	}

	/**
	 * Take a new choice the way {@link #set} does, and report each row that changes place as one move, in an order the list can replay. A list told of moves
	 * rather than changes keeps each row's view, so TalkBack's focus stays on a row as it travels.
	 *
	 * @param choice the new choice
	 * @param moved  told of each move: the row's place before it, then after
	 */
	void change(List<DetailFamily> choice, BiConsumer<Integer, Integer> moved) {
		final List<Object> shown = keys();
		set(choice);
		final List<Object> wanted = keys();
		for (int to = 0; to < wanted.size(); to++) {
			final int from = shown.indexOf(wanted.get(to));
			if (from != to) {
				shown.add(to, shown.remove(from));
				moved.accept(FIRST_ROW + from, FIRST_ROW + to);
			}
		}
	}

	/**
	 * Move a row into the place of the row or divider next to it. The divider counts as a place, so a row moved across it changes sides: ticked when it rises
	 * above, unticked when it sinks below.
	 *
	 * @param from the row's place
	 * @param to   the place of the row or divider it passed
	 * @return whether the row changed sides
	 */
	boolean move(int from, int to) {
		final DetailFamily family = familyAt(from);
		final boolean wasChosen = isChosenPlace(from);
		// Taking a chosen row out moves the divider up a place, so landing on the divider's old place puts the row just below it.
		final boolean nowChosen = wasChosen ? to < divider() : to <= divider();
		(wasChosen ? chosen : unchosen).remove(family);
		if (nowChosen) {
			chosen.add(to - FIRST_ROW, family);
		} else {
			// Counted against the divider as it stands now, after the row left its side.
			unchosen.add(to - divider() - 1, family);
		}
		return wasChosen != nowChosen;
	}

	/** Each row from the first one down: its family, or {@link #DIVIDER}. */
	private List<Object> keys() {
		final List<Object> keys = new ArrayList<>(size() - FIRST_ROW);
		keys.addAll(chosen);
		keys.add(DIVIDER);
		keys.addAll(unchosen);
		return keys;
	}
}
