package com.almothafar.simplebatterynotifier.model;

import android.os.BatteryManager;

/**
 * What the battery is drawing from, decoded from {@code BatteryManager.EXTRA_PLUGGED}. The one decoding, so the details table and the status notification can
 * word a source differently ("USB Port" there, "USB" in the notification) without disagreeing about which source it is.
 */
public enum PowerSource {
	BATTERY,
	USB,
	AC,
	WIRELESS,
	/** Plugged into something the app has no name for: a dock (API 33+), or a type added later. */
	OTHER;

	/**
	 * Decode a plugged reading.
	 *
	 * @param plugged the {@code EXTRA_PLUGGED} value: 0 on battery, otherwise one of the {@code BATTERY_PLUGGED_*} constants
	 * @return the source it names
	 */
	public static PowerSource fromPlugged(int plugged) {
		return switch (plugged) {
			case 0 -> BATTERY;
			case BatteryManager.BATTERY_PLUGGED_USB -> USB;
			case BatteryManager.BATTERY_PLUGGED_AC -> AC;
			case BatteryManager.BATTERY_PLUGGED_WIRELESS -> WIRELESS;
			default -> OTHER;
		};
	}
}
