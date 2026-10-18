package com.almothafar.simplebatterynotifier.model;

import static java.util.Objects.isNull;

/**
 * The families the notification details are chosen from (#356) — the registry behind the ongoing notification's collapsed line. Each family renders its own
 * state-appropriate form ({@link #SPEED} is the drain rate while discharging and the charge power while charging), so a chosen slot is never dead for half the
 * day. The forms live in {@code OngoingStatusContent}, whose switch over this enum has no default: a new family does not compile without one.
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

	private final String storageId;

	DetailFamily(String storageId) {
		this.storageId = storageId;
	}

	/**
	 * @return the id this family is persisted under
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
		if (isNull(storageId)) {
			return null;
		}
		for (DetailFamily family : values()) {
			if (family.storageId.equals(storageId)) {
				return family;
			}
		}
		return null;
	}
}
