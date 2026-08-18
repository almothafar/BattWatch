package com.almothafar.simplebatterynotifier.service;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.BidiVisualOrder;
import com.almothafar.simplebatterynotifier.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * What the alert copy actually <em>renders</em> as under Arabic (#275) — not what it contains.
 * <p>
 * Every existing Arabic test in this package asserts with {@code contains}, and {@code contains} is blind to the defect here: a notification that reads
 * "البطارية عند %20" contains {@code "20%"} just as happily as one that reads "البطارية عند 20%". The digits are an LTR island inside RTL text and the
 * characters touching them — {@code %}, {@code °}, a unit — are neutrals the bidi algorithm hands to whichever direction surrounds them, so they end up on the
 * far side of the number they belong to. {@link BidiVisualOrder} lays the string out with the real algorithm and gives back the order it is drawn in, which is
 * the only form the property can be stated in.
 * <p>
 * These call the production entry points rather than re-assembling their strings, so a call site that stops isolating fails here. That matters more than usual
 * on this defect: the fix is one call per surface and nothing at the perimeter enforces it, so the next surface added is the next one to ship broken.
 * <p>
 * Three call sites are deliberately absent, because no assertion here could fail on them — each was checked by deleting its {@code isolate} and watching the
 * suite stay green:
 * <ul>
 *     <li>the two wattage messages. "~18 واط" carries an Arabic unit word, so the number is the only left-to-right run and a lone run is drawn in place
 *     whether it is isolated or not. Their Western digits (#273) are covered by {@code NotificationServiceTest.ChargeConnectedDigits}.</li>
 *     <li>{@code OngoingStatusContent.statusTitle}. Its percentage opens the string, so nothing strong precedes it and the algorithm never retypes its digits
 *     — the case only arises once Arabic letters sit on the left of a number.</li>
 * </ul>
 * All three stay isolated defensively, against the copy ever growing a word in front of the number or a Latin unit behind it. That they are unpinnable is a
 * property of where they sit in their sentence, not an oversight. The alert copy below is where layout actually goes wrong.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "ar-rEG")
public class ArabicAlertRenderingTest {

	private Context context;
	private SharedPreferences prefs;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
		prefs = PreferenceManager.getDefaultSharedPreferences(context);

		// Premise: a region-bearing Arabic locale really is loaded. Under a bare "ar" CLDR picks Western digits anyway and half of what follows would pass
		// whether the code were right or wrong — the same trap #241 fell into.
		assertEquals("ar-rEG should select Eastern Arabic digits", "٢٠", String.format(Locale.getDefault(), "%d", 20));
	}

	/**
	 * The text of the single notification the preceding call posted.
	 *
	 * @return the collapsed content line, as the shade would draw it
	 */
	private String postedText() {
		final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		final Notification posted = shadowOf(manager).getAllNotifications().get(0);
		return String.valueOf(posted.extras.getCharSequence(Notification.EXTRA_TEXT));
	}

	@Test
	public void criticalAlert_keepsThePercentSignOnItsLevel() {
		prefs.edit().putInt(context.getString(R.string._pref_key_critical_battery_level), 15).commit();

		final NotificationConfig config = new NotificationConfig(context, prefs, AlertType.CRITICAL, 10);

		// values-ar was really loaded, so these are assertions about the Arabic copy and not an English fallback.
		assertTrue(config.content, config.content.contains("البطارية"));
		BidiVisualOrder.assertRendersAsWritten(config.ticker, "15%");
		BidiVisualOrder.assertRendersAsWritten(config.content, "15%");
		BidiVisualOrder.assertRendersAsWritten(config.bigContent, "15%");
	}

	@Test
	public void warningAlert_keepsThePercentSignOnItsLevel() {
		prefs.edit().putInt(context.getString(R.string._pref_key_warn_battery_level), 35).commit();

		final NotificationConfig config = new NotificationConfig(context, prefs, AlertType.WARNING, 30);

		assertTrue(config.content, config.content.contains("البطارية"));
		BidiVisualOrder.assertRendersAsWritten(config.ticker, "35%");
		BidiVisualOrder.assertRendersAsWritten(config.content, "35%");
		BidiVisualOrder.assertRendersAsWritten(config.bigContent, "35%");
	}

	/**
	 * The almost-full alert names two different numbers — the level reached and the target it crossed (#263) — so both have to survive, in copy whose long form
	 * also carries literal percentages of its own that this says nothing about.
	 */
	@Test
	public void almostFullAlert_keepsThePercentSignOnBothItsNumbers() {
		prefs.edit().putInt(context.getString(R.string._pref_key_charge_target), 90).commit();

		final NotificationConfig config = new NotificationConfig(context, prefs, AlertType.FULL, 95);

		assertTrue(config.content, config.content.contains("هدف الشحن"));
		BidiVisualOrder.assertRendersAsWritten(config.ticker, "95%");
		BidiVisualOrder.assertRendersAsWritten(config.content, "90%");
		BidiVisualOrder.assertRendersAsWritten(config.bigContent, "90%");
	}

	/**
	 * The worst of them before the fix: "32.0 °C" reached an Arabic user as "C° 32.0", the unit detached from its number <em>and</em> reversed, because both
	 * the degree sign and the space are neutrals.
	 */
	@Test
	public void temperatureAlert_keepsTheUnitOnItsReading() {
		NotificationService.sendTemperatureNotification(context, 460);

		final String text = postedText();
		assertTrue(text, text.contains("حرارة"));
		BidiVisualOrder.assertRendersAsWritten(text, "46.0 °C");
	}

	/**
	 * Both figures in the drain warning carry a unit that used to live in the resource, outside anything a caller could isolate: the rate rendered as
	 * {@code %27} and the user's own limit as {@code h/%20}. Fixing it moved the units into the arguments, which is the shape the rest of this class relies on.
	 */
	@Test
	public void fastDrainAlert_keepsBothUnitsOnTheirNumbers() {
		NotificationService.sendFastDrainNotification(context, 27, 20, 45);

		final String text = postedText();
		assertTrue(text, text.contains("الساعة"));
		BidiVisualOrder.assertRendersAsWritten(text, "27%");
		BidiVisualOrder.assertRendersAsWritten(text, "20%/h");
	}
}
