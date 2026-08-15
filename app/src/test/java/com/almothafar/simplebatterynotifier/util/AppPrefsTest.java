package com.almothafar.simplebatterynotifier.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.util.Xml;

import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.model.LevelThresholds;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link AppPrefs} facade (#162). The context-backed cases run under Robolectric (typed
 * accessors fall back to the single-owned defaults, read stored values, round-trip writes, and — the
 * drift guard — the XML slider defaults still equal the constants); the pure drain-limit clamp is a
 * plain parameterized test.
 */
@RunWith(Enclosed.class)
public class AppPrefsTest {

	@RunWith(RobolectricTestRunner.class)
	@Config(sdk = 34)
	public static class ContextBacked {

		private Context context;

		@Before
		public void setUp() {
			context = ApplicationProvider.getApplicationContext();
		}

		@Test
		public void levels_fallBackToTheSingleOwnedDefaults() {
			assertEquals(AppPrefs.DEFAULT_CRITICAL_LEVEL, AppPrefs.criticalLevel(context));
			assertEquals(AppPrefs.DEFAULT_WARNING_LEVEL, AppPrefs.warningLevel(context));
			// Pin the historically-duplicated 20/40 literals these constants replaced.
			assertEquals(20, AppPrefs.DEFAULT_CRITICAL_LEVEL);
			assertEquals(40, AppPrefs.DEFAULT_WARNING_LEVEL);
		}

		@Test
		public void levels_readBackStoredValues() {
			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_critical_battery_level), 11)
			                 .putInt(context.getString(R.string._pref_key_warn_battery_level), 33)
			                 .apply();

			assertEquals(11, AppPrefs.criticalLevel(context));
			assertEquals(33, AppPrefs.warningLevel(context));
		}

		@Test
		public void batteryLevels_bundlesCriticalAndWarning() {
			AppPrefs.setBatteryLevels(context, new LevelThresholds(15, 35));

			assertEquals(new LevelThresholds(15, 35), AppPrefs.batteryLevels(context));
		}

		@Test
		public void setBatteryLevels_persistsBothThresholdsUnderTheSharedKeys() {
			AppPrefs.setBatteryLevels(context, new LevelThresholds(15, 35));

			// Round-trips through the typed getters...
			assertEquals(15, AppPrefs.criticalLevel(context));
			assertEquals(35, AppPrefs.warningLevel(context));

			// ...and lands under the exact keys the sliders and alert engine read directly.
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			assertEquals(15, prefs.getInt(context.getString(R.string._pref_key_critical_battery_level), -1));
			assertEquals(35, prefs.getInt(context.getString(R.string._pref_key_warn_battery_level), -1));
		}

		@Test
		public void drainLimitPph_defaultsWhenUnsetAndClampsStoredValue() {
			assertEquals(AppPrefs.DEFAULT_DRAIN_LIMIT_PPH, AppPrefs.drainLimitPph(context));

			// A corrupt out-of-range stored value is clamped on read, not returned raw.
			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_fast_drain_limit), 999)
			                 .apply();
			assertEquals(AppPrefs.MAX_DRAIN_LIMIT_PPH, AppPrefs.drainLimitPph(context));
		}

		@Test
		public void chargeTarget_defaultsToTheAdviceTheAppGivesAndClampsStoredValues() {
			// 90 is the app's own "unplug before 100%" guidance, now something the alert can act on.
			assertEquals(90, AppPrefs.DEFAULT_CHARGE_TARGET);
			assertEquals(AppPrefs.DEFAULT_CHARGE_TARGET, AppPrefs.chargeTarget(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_charge_target), 999)
			                 .apply();
			assertEquals(AppPrefs.MAX_CHARGE_TARGET, AppPrefs.chargeTarget(context));
		}

		@Test
		public void chargeTarget_readsBackAStoredValue() {
			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_charge_target), 85)
			                 .apply();

			assertEquals(85, AppPrefs.chargeTarget(context));
		}

		@Test
		public void vibrateEnabled_defaultsTrueAndReadsBack() {
			// Defaults on (matches the switch's android:defaultValue in pref_behaviour.xml).
			assertTrue(AppPrefs.DEFAULT_VIBRATE);
			assertTrue(AppPrefs.vibrateEnabled(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putBoolean(context.getString(R.string._pref_key_notifications_vibrate), false)
			                 .apply();
			assertFalse(AppPrefs.vibrateEnabled(context));
		}

		/**
		 * The drift guard for the one restatement the facade can't own: the range slider's XML-declared
		 * defaults in {@code pref_alerts.xml} must equal the {@link AppPrefs} constants, since the
		 * framework instantiates the slider from XML and its attr wins as that control's default.
		 */
		@Test
		public void xmlSliderDefaults_matchTheFacadeConstants() throws Exception {
			final XmlResourceParser parser = context.getResources().getXml(R.xml.pref_alerts);
			Integer xmlCritical = null;
			Integer xmlWarning = null;

			for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
				if (event != XmlPullParser.START_TAG || !parser.getName().endsWith("BatteryRangeSliderPreference")) {
					continue;
				}
				for (int i = 0; i < parser.getAttributeCount(); i++) {
					final int attrRes = parser.getAttributeNameResource(i);
					if (attrRes == R.attr.criticalDefault) {
						xmlCritical = parser.getAttributeIntValue(i, Integer.MIN_VALUE);
					} else if (attrRes == R.attr.warningDefault) {
						xmlWarning = parser.getAttributeIntValue(i, Integer.MIN_VALUE);
					}
				}
			}

			assertNotNull("criticalDefault attr missing from pref_alerts.xml", xmlCritical);
			assertNotNull("warningDefault attr missing from pref_alerts.xml", xmlWarning);
			assertEquals(AppPrefs.DEFAULT_CRITICAL_LEVEL, (int) xmlCritical);
			assertEquals(AppPrefs.DEFAULT_WARNING_LEVEL, (int) xmlWarning);
		}

		/**
		 * The same drift guard for the charge-target slider (#263), but asserted against a real
		 * {@link SeekBarPreference} built from the XML rather than against the attribute text.
		 * <p>
		 * Reading the attributes would not have caught the bug it exists to catch. AndroidX declares
		 * {@code min} in its <em>own</em> namespace — only {@code max} and {@code layout} come from
		 * {@code android:} — so an {@code android:min="80"} is parsed by nobody and the slider silently
		 * runs from 0. The screen then offers 80 positions the clamp rejects, and a user who picks one
		 * sees a target the alert engine will never act on. Inflating the preference is the only
		 * assertion that can tell the two apart.
		 */
		@Test
		public void chargeTargetSlider_boundsAreTheFacadeConstantsAtRuntime() throws Exception {
			final SeekBarPreference slider = inflateChargeTargetSlider();

			assertNotNull("charge-target slider missing from pref_alerts.xml", slider);
			assertEquals(AppPrefs.MIN_CHARGE_TARGET, slider.getMin());
			assertEquals(AppPrefs.MAX_CHARGE_TARGET, slider.getMax());
		}

		/**
		 * The XML-declared default is the one value the framework owns rather than {@link AppPrefs}, so it
		 * is still read from the attribute — that <em>is</em> where the framework reads it from.
		 */
		@Test
		public void xmlChargeTargetDefault_matchesTheFacadeConstant() throws Exception {
			final XmlResourceParser parser = context.getResources().getXml(R.xml.pref_alerts);
			Integer xmlDefault = null;

			try {
				for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
					if (event != XmlPullParser.START_TAG || !isChargeTargetSlider(parser)) {
						continue;
					}
					for (int i = 0; i < parser.getAttributeCount(); i++) {
						if (parser.getAttributeNameResource(i) == android.R.attr.defaultValue) {
							xmlDefault = parser.getAttributeIntValue(i, Integer.MIN_VALUE);
						}
					}
				}
			} finally {
				parser.close();
			}

			assertNotNull("defaultValue attr missing from the charge-target slider", xmlDefault);
			assertEquals(AppPrefs.DEFAULT_CHARGE_TARGET, (int) xmlDefault);
		}

		/**
		 * Builds the charge-target slider from {@code pref_alerts.xml} exactly as the preference framework
		 * would, so its bounds are the ones a user's finger actually meets.
		 *
		 * @return the inflated slider, or {@code null} when the screen no longer declares one
		 */
		private SeekBarPreference inflateChargeTargetSlider() throws Exception {
			final XmlResourceParser parser = context.getResources().getXml(R.xml.pref_alerts);
			try {
				for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
					if (event == XmlPullParser.START_TAG && isChargeTargetSlider(parser)) {
						return new SeekBarPreference(context, Xml.asAttributeSet(parser));
					}
				}
			} finally {
				parser.close();
			}
			return null;
		}

		/**
		 * Whether the tag the parser is on is the charge-target slider, identified by its preference key
		 * rather than its position — the screen grows and the categories get reordered.
		 */
		private boolean isChargeTargetSlider(XmlResourceParser parser) {
			for (int i = 0; i < parser.getAttributeCount(); i++) {
				if (parser.getAttributeNameResource(i) == android.R.attr.key
						&& parser.getAttributeResourceValue(i, 0) == R.string._pref_key_charge_target) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * {@link AppPrefs#clampChargeTarget}: the same guarantee for the charge target (#263). Both
	 * directions matter — a stored 0 is what a downgraded or restored preferences file yields, and
	 * without the lower clamp it would fire the "almost full" alert at empty.
	 */
	@RunWith(Parameterized.class)
	public static class ClampChargeTarget {

		@Parameter(0) public int stored;
		@Parameter(1) public int expected;

		@Parameters(name = "clampChargeTarget({0}) = {1}")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{
					{90, 90},                                     // in range: unchanged
					{AppPrefs.MIN_CHARGE_TARGET, 80},             // boundaries kept
					{AppPrefs.MAX_CHARGE_TARGET, 100},
					{79, 80},                                     // below min: clamped up
					{0, 80},
					{-5, 80},
					{101, 100},                                   // above max: clamped down
					{999, 100},
			});
		}

		@Test
		public void matchesExpected() {
			assertEquals(expected, AppPrefs.clampChargeTarget(stored));
		}
	}

	/**
	 * {@link AppPrefs#reArmLevel} and {@link AppPrefs#targetIsAFullCharge}: the two derivations the alert
	 * engine, the notification copy and the temperature range all share (#263).
	 */
	public static class TargetDerivations {

		@Test
		public void reArmLevel_sitsAFixedMarginBelowTheTarget() {
			assertEquals(85, AppPrefs.reArmLevel(90));
			// At the maximum this is the ≤95 band the full alert re-armed on before the target existed.
			assertEquals(95, AppPrefs.reArmLevel(AppPrefs.MAX_CHARGE_TARGET));
		}

		@Test
		public void targetIsAFullCharge_onlyAtTheMaximum() {
			assertTrue(AppPrefs.targetIsAFullCharge(AppPrefs.MAX_CHARGE_TARGET));
			assertFalse(AppPrefs.targetIsAFullCharge(AppPrefs.DEFAULT_CHARGE_TARGET));
			assertFalse(AppPrefs.targetIsAFullCharge(99));
		}
	}

	/**
	 * {@link AppPrefs#clampDrainLimit}: a stored limit outside the slider's range is clamped, so a
	 * corrupt preference can't skew the red line or the #109 trigger. Pure, so no context is needed.
	 */
	@RunWith(Parameterized.class)
	public static class ClampDrainLimit {

		@Parameter(0) public int stored;
		@Parameter(1) public int expected;

		@Parameters(name = "clampDrainLimit({0}) = {1}")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{
					{20, 20},                                     // in range: unchanged
					{AppPrefs.MIN_DRAIN_LIMIT_PPH, 5},            // boundaries kept
					{AppPrefs.MAX_DRAIN_LIMIT_PPH, 60},
					{0, 5},                                       // below min: clamped up
					{-3, 5},
					{999, 60},                                    // above max: clamped down
			});
		}

		@Test
		public void matchesExpected() {
			assertEquals(expected, AppPrefs.clampDrainLimit(stored));
		}
	}
}
