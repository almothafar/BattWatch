package com.almothafar.simplebatterynotifier.model;

import java.util.HashMap;
import java.util.Map;

/**
 * The families the notification details are chosen from (#356) — the registry behind the ongoing notification's collapsed line. Each family renders its own
 * state-appropriate form ({@link #SPEED} is the drain rate while discharging, and while charging the charge power, or the charge rate when the power cannot be
 * estimated), so a chosen slot is never dead for half the day. The forms live in {@code OngoingStatusContent}, whose switch over this enum has no default: a
 * new family does not compile without one.
 * <p>
 * Declaration order is the order the picker lists them in. What persists is {@link #storageId()}, never the constant's name, so renaming a constant cannot
 * orphan a saved selection.
 */
public enum DetailFamily {
	SPEED("speed"),
	CURRENT("current"),
	TIME("time"),
	TEMPERATURE("temperature"),
	POWER_SOURCE("power_source");

	/** The most families the collapsed line carries at once (#356). */
	public static final int MAX_SELECTED = 3;

	/** Every family by its storage id, built once: the saved choice is parsed again on every battery broadcast. */
	private static final Map<String, DetailFamily> BY_STORAGE_ID = new HashMap<>();

	static {
		for (final DetailFamily family : values()) {
			BY_STORAGE_ID.put(family.storageId, family);
		}
	}

	private final String storageId;

	DetailFamily(String storageId) {
		this.storageId = storageId;
	}

	/**
	 * The id this family is persisted under, which stays put when the constant is renamed.
	 *
	 * @return the storage id
	 */
	public String storageId() {
		return storageId;
	}

	/**
	 * The family persisted under an id.
	 *
	 * @param storageId a persisted id, possibly from a newer build or a corrupt value
	 * @return the matching family, or null when the id names none
	 */
	public static DetailFamily fromStorageId(String storageId) {
		return BY_STORAGE_ID.get(storageId);
	}
}
