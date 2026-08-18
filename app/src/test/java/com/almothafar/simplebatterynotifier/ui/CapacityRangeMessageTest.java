package com.almothafar.simplebatterynotifier.ui;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.BidiVisualOrder;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link BatteryInsightsActivity#capacityRangeMessage(Context)} — the Toast shown when a typed design capacity falls outside the accepted bounds.
 * <p>
 * The bounds are the one place in the app where a Latin unit follows a number in translated prose, and they were the case #275 got wrong twice: first by not
 * isolating at all, then by isolating the bare number and leaving the {@code "mAh"} behind in the resource, where it still reordered to the far side of the
 * digits it belongs to. What is pinned here is that each bound reaches the reader as one piece.
 */
@RunWith(Enclosed.class)
public class CapacityRangeMessageTest {

	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34)
	public static class UnderAnLtrLocale {

		@Test
		public void namesBothBoundsWithTheirUnit() {
			final String message = BatteryInsightsActivity.capacityRangeMessage(ApplicationProvider.getApplicationContext());

			// The unit now arrives with each bound rather than once at the end of the sentence, so it is named twice.
			assertEquals("Enter a capacity between 500 mAh and 15000 mAh", message);
		}
	}

	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34, qualifiers = "ar-rEG")
	public static class UnderAnRtlLocale {

		@Test
		public void keepsTheUnitOnBothBounds() {
			// Premise: values-ar is loaded and its digits would localise, so what follows is about the Arabic message.
			assertEquals("ar-rEG should select Eastern Arabic digits", "٥٠٠", String.format(Locale.getDefault(), "%d", 500));

			final Context context = ApplicationProvider.getApplicationContext();
			final String message = BatteryInsightsActivity.capacityRangeMessage(context);

			assertTrue(message, message.contains("أدخل سعة"));
			assertTrue("Eastern digits leaked into the range message: " + message, !message.contains("٥٠٠"));

			// Both bounds, unit included. Isolating only the digits leaves "mAh" a neutral run in Arabic prose, and it lands on the number's far side.
			BidiVisualOrder.assertRendersAsWritten(message, "500 mAh");
			BidiVisualOrder.assertRendersAsWritten(message, "15000 mAh");
		}
	}
}
