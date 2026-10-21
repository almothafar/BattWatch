package com.almothafar.simplebatterynotifier.model;

import android.os.BatteryManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * {@link PowerSource#fromPlugged}: the one decoding of {@code EXTRA_PLUGGED} that the details table and the status notification both word from. The case
 * that mattered is the last two rows — a charger Android reports but the app has no name for used to read "On Battery" in the details table.
 */
@RunWith(Parameterized.class)
public class PowerSourceTest {

	@Parameter public int plugged;
	@Parameter(1) public PowerSource expected;

	@Parameters(name = "fromPlugged({0}) = {1}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{
				{0, PowerSource.BATTERY},
				{BatteryManager.BATTERY_PLUGGED_USB, PowerSource.USB},
				{BatteryManager.BATTERY_PLUGGED_AC, PowerSource.AC},
				{BatteryManager.BATTERY_PLUGGED_WIRELESS, PowerSource.WIRELESS},
				{8, PowerSource.OTHER},   // BATTERY_PLUGGED_DOCK, API 33
				{99, PowerSource.OTHER},  // whatever comes next
		});
	}

	@Test
	public void matchesExpected() {
		assertEquals(expected, PowerSource.fromPlugged(plugged));
	}
}
