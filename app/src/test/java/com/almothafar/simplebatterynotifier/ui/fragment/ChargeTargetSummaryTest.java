package com.almothafar.simplebatterynotifier.ui.fragment;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.BidiVisualOrder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link GenericPreferenceFragment#chargeTargetSummary(Context, int)} — the line under the charge-target slider, which names the target percentage (#263).
 * <p>
 * A settings screen is not a notification, but the bidi property is the same one and so is the risk: the summary is re-applied on every resume, and the
 * percentage is the only left-to-right run in an Arabic sentence, so its {@code '%'} reorders away from its digits unless the whole quantity is isolated
 * (#275). Nothing else in the suite covers this call site.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "ar-rEG")
public class ChargeTargetSummaryTest {

	@Test
	public void keepsThePercentSignOnTheTarget() {
		// Premise: this locale really does localise digits, so the assertions below are not vacuous.
		assertEquals("ar-rEG should select Eastern Arabic digits", "٩٠", String.format(Locale.getDefault(), "%d", 90));

		final Context context = ApplicationProvider.getApplicationContext();
		final String summary = GenericPreferenceFragment.chargeTargetSummary(context, 90);

		assertTrue(summary, summary.contains("تنبيه عند"));
		BidiVisualOrder.assertRendersAsWritten(summary, "90%");
	}

	/**
	 * A target of 100 takes the other branch — different string, same property, and the one whose copy says "full charge" rather than "nearly done".
	 */
	@Test
	public void keepsThePercentSignOnAFullChargeTarget() {
		final Context context = ApplicationProvider.getApplicationContext();
		final String summary = GenericPreferenceFragment.chargeTargetSummary(context, 100);

		assertTrue(summary, summary.contains("شحن كامل"));
		BidiVisualOrder.assertRendersAsWritten(summary, "100%");
	}
}
