package com.almothafar.simplebatterynotifier.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.util.Xml;

import androidx.appcompat.app.AppCompatDelegate;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link AppPrefs} facade (#162). The context-backed cases run under Robolectric (typed
 * accessors fall back to the single-owned defaults, read stored values, round-trip writes, and — the
 * drift guard — the XML slider defaults still equal the constants); the pure drain-limit, charge-target and minutes clamps are plain parameterized tests.
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
		public void themeMode_fallsBackToFollowingTheSystem() {
			assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppPrefs.themeMode(context));
		}

		@Test
		public void themeMode_readsBackTheStoredChoice() {
			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putString(context.getString(R.string._pref_key_theme), AppPrefs.THEME_DARK)
			                 .apply();

			assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppPrefs.themeMode(context));
		}

		/** Drift guard: the picker persists whatever arrays.xml lists, so a rename there would silently strand the mapping. */
		@Test
		public void themeValuesArray_stillMatchesTheConstants() {
			assertArrayEquals(new String[]{AppPrefs.THEME_SYSTEM, AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK},
			                  context.getResources().getStringArray(R.array.theme_values));
		}

		/**
		 * The other half of that guard, and the one the array does not cover: {@code PreferenceManager} writes the picker's XML default on first run, so a
		 * default of {@code "light"} there would open a fresh install in light whatever {@link AppPrefs#themeMode} falls back to.
		 */
		@Test
		public void themeDefaultInXml_stillMatchesTheConstant() throws Exception {
			assertEquals(AppPrefs.THEME_SYSTEM, xmlStringDefaultOf(R.xml.pref_general, R.string._pref_key_theme));
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
			PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(context.getString(R.string._pref_key_fast_drain_limit), 999).apply();
			assertEquals(AppPrefs.MAX_DRAIN_LIMIT_PPH, AppPrefs.drainLimitPph(context));
		}

		@Test
		public void chargeTarget_defaultsToTheAdviceTheAppGivesAndClampsStoredValues() {
			// 90 is the app's own "unplug before 100%" guidance, now something the alert can act on.
			assertEquals(90, AppPrefs.DEFAULT_CHARGE_TARGET);
			assertEquals(AppPrefs.DEFAULT_CHARGE_TARGET, AppPrefs.chargeTarget(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(context.getString(R.string._pref_key_charge_target), 999).apply();
			assertEquals(AppPrefs.MAX_CHARGE_TARGET, AppPrefs.chargeTarget(context));
		}

		@Test
		public void chargeTarget_readsBackAStoredValue() {
			PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(context.getString(R.string._pref_key_charge_target), 85).apply();

			assertEquals(85, AppPrefs.chargeTarget(context));
		}

		@Test
		public void unplugReminder_defaultsOffAndReadsBack() {
			// Off is the whole point of the default (#264): the alert stays once per charge for everyone who didn't ask to be nagged.
			assertFalse(AppPrefs.DEFAULT_UNPLUG_REMINDER);
			assertFalse(AppPrefs.unplugReminderEnabled(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putBoolean(context.getString(R.string._pref_key_notify_unplug_reminder), true)
			                 .apply();
			assertTrue(AppPrefs.unplugReminderEnabled(context));
		}

		@Test
		public void unplugReminderGap_defaultsToTheSliderDefaultAndClampsStoredValues() {
			assertEquals(AppPrefs.DEFAULT_UNPLUG_REMINDER_MINUTES * 60_000L, AppPrefs.unplugReminderGapMs(context));

			// A 0-minute gap is what a corrupt or downgraded preferences file yields, and it would remind on every battery broadcast.
			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_unplug_reminder_minutes), 0)
			                 .apply();
			assertEquals(AppPrefs.MIN_UNPLUG_REMINDER_MINUTES * 60_000L, AppPrefs.unplugReminderGapMs(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_unplug_reminder_minutes), 30)
			                 .apply();
			assertEquals(30 * 60_000L, AppPrefs.unplugReminderGapMs(context));
		}

		/**
		 * The fast-drain window (#109), now that the facade owns it. A 0-minute window is the corrupt-preference case that matters: it would fire on the first
		 * broadcast above the limit, turning the sustained-drain warning into the spike alarm the window exists to prevent.
		 */
		@Test
		public void fastDrainSustained_defaultsToTheSliderDefaultAndClampsStoredValues() {
			assertEquals(AppPrefs.DEFAULT_FAST_DRAIN_SUSTAINED_MINUTES * 60_000L, AppPrefs.fastDrainSustainedMs(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_fast_drain_sustained_minutes), 0)
			                 .apply();
			assertEquals(AppPrefs.MIN_FAST_DRAIN_SUSTAINED_MINUTES * 60_000L, AppPrefs.fastDrainSustainedMs(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_fast_drain_sustained_minutes), 999)
			                 .apply();
			assertEquals(AppPrefs.MAX_FAST_DRAIN_SUSTAINED_MINUTES * 60_000L, AppPrefs.fastDrainSustainedMs(context));
		}

		@Test
		public void fastDrainReminderGap_defaultsToTheSliderDefaultAndClampsStoredValues() {
			assertEquals(AppPrefs.DEFAULT_FAST_DRAIN_REMINDER_MINUTES * 60_000L, AppPrefs.fastDrainReminderGapMs(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_fast_drain_reminder_minutes), 0)
			                 .apply();
			assertEquals(AppPrefs.MIN_FAST_DRAIN_REMINDER_MINUTES * 60_000L, AppPrefs.fastDrainReminderGapMs(context));

			PreferenceManager.getDefaultSharedPreferences(context).edit()
			                 .putInt(context.getString(R.string._pref_key_fast_drain_reminder_minutes), 30)
			                 .apply();
			assertEquals(30 * 60_000L, AppPrefs.fastDrainReminderGapMs(context));
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
		 * The one value here that is no setting: the flag {@code PowerConnectionService} leaves when Android refuses the foreground promotion, and
		 * {@code MainActivity} clears once it has explained the interruption (#302).
		 */
		@Test
		public void monitoringStopped_defaultsToNothingToReportAndRoundTrips() {
			assertFalse("nothing to report until an interruption actually happens", AppPrefs.monitoringStopped(context));

			AppPrefs.setMonitoringStopped(context, true);
			assertTrue("the interruption has to outlive the process that recorded it", AppPrefs.monitoringStopped(context));

			AppPrefs.setMonitoringStopped(context, false);
			assertFalse("reporting it clears it, so the hint shows once", AppPrefs.monitoringStopped(context));
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
		 * The same drift guard for the charge-target slider (#263), but asserted against a real {@link SeekBarPreference} built from the XML rather than
		 * against the attribute text.
		 * <p>
		 * Reading the attributes would not have caught the bug it exists to catch. AndroidX declares {@code min} in its <em>own</em> namespace — only
		 * {@code max} and {@code layout} come from {@code android:} — so an {@code android:min="80"} is parsed by nobody and the slider silently runs from 0.
		 * The screen then offers 80 positions the clamp rejects, and a user who picks one sees a target the alert engine will never act on. Inflating the
		 * preference is the only assertion that can tell the two apart.
		 */
		@Test
		public void chargeTargetSlider_boundsAreTheFacadeConstantsAtRuntime() throws Exception {
			final SeekBarPreference slider = inflateSlider(R.string._pref_key_charge_target);

			assertNotNull("charge-target slider missing from pref_alerts.xml", slider);
			assertEquals(AppPrefs.MIN_CHARGE_TARGET, slider.getMin());
			assertEquals(AppPrefs.MAX_CHARGE_TARGET, slider.getMax());
		}

		/**
		 * The same guard for the unplug-reminder interval (#264), which restates the facade's bounds in XML exactly as the charge target does — including the
		 * {@code app:min} trap that would otherwise offer gaps down to 0 minutes: a reminder on every broadcast, which is the one behaviour the clamp exists to
		 * make unreachable.
		 */
		@Test
		public void unplugReminderSlider_boundsAreTheFacadeConstantsAtRuntime() throws Exception {
			final SeekBarPreference slider = inflateSlider(R.string._pref_key_unplug_reminder_minutes);

			assertNotNull("unplug-reminder slider missing from pref_alerts.xml", slider);
			assertEquals(AppPrefs.MIN_UNPLUG_REMINDER_MINUTES, slider.getMin());
			assertEquals(AppPrefs.MAX_UNPLUG_REMINDER_MINUTES, slider.getMax());
		}

		/**
		 * The XML-declared default is the one value the framework owns rather than {@link AppPrefs}, so it is still read from the attribute — that <em>is</em>
		 * where the framework reads it from.
		 */
		@Test
		public void xmlChargeTargetDefault_matchesTheFacadeConstant() throws Exception {
			final Integer xmlDefault = xmlDefaultOf(R.string._pref_key_charge_target);

			assertNotNull("defaultValue attr missing from the charge-target slider", xmlDefault);
			assertEquals(AppPrefs.DEFAULT_CHARGE_TARGET, (int) xmlDefault);
		}

		/**
		 * The unplug reminder greys out with the alert it repeats, and its interval greys out with the reminder (#264).
		 * <p>
		 * The chain is the design decision, not an accident of ordering: there is nothing to repeat when the full-battery alert is off, so leaving these two
		 * live would offer a user settings that cannot do anything. It is also what separates them from the charge target directly above, which stays live
		 * precisely because it still governs the Insights temperature range (#260) with the alert switched off.
		 */
		@Test
		public void unplugReminderControls_greyOutWithTheAlertTheyRepeat() throws Exception {
			assertEquals(R.string._pref_key_notify_for_full_level, xmlDependencyOf(R.string._pref_key_notify_unplug_reminder));
			assertEquals(R.string._pref_key_notify_unplug_reminder, xmlDependencyOf(R.string._pref_key_unplug_reminder_minutes));
			// The contrast that makes the rule above a rule rather than a habit.
			assertEquals("the charge target must stay live with the alert off", 0, xmlDependencyOf(R.string._pref_key_charge_target));
		}

		@Test
		public void xmlUnplugReminderDefault_matchesTheFacadeConstant() throws Exception {
			final Integer xmlDefault = xmlDefaultOf(R.string._pref_key_unplug_reminder_minutes);

			assertNotNull("defaultValue attr missing from the unplug-reminder slider", xmlDefault);
			assertEquals(AppPrefs.DEFAULT_UNPLUG_REMINDER_MINUTES, (int) xmlDefault);
		}

		/**
		 * The same guard for the two fast-drain timing sliders (#109), whose bounds moved into this facade so that "the slider range is also the enforced
		 * range" is stated once. Until now that pairing was only a comment in {@code pref_alerts.xml}; these are the assertions that make it a rule.
		 * <p>
		 * The sustained window is the one where the {@code app:min} trap bites hardest: a slider bottoming out at 0 offers a zero-minute window, which is the
		 * spike alarm the whole "sustained, not spikes" design exists to rule out.
		 */
		@Test
		public void fastDrainSustainedSlider_boundsAreTheFacadeConstantsAtRuntime() throws Exception {
			final SeekBarPreference slider = inflateSlider(R.string._pref_key_fast_drain_sustained_minutes);

			assertNotNull("fast-drain sustained slider missing from pref_alerts.xml", slider);
			assertEquals(AppPrefs.MIN_FAST_DRAIN_SUSTAINED_MINUTES, slider.getMin());
			assertEquals(AppPrefs.MAX_FAST_DRAIN_SUSTAINED_MINUTES, slider.getMax());
		}

		@Test
		public void fastDrainReminderSlider_boundsAreTheFacadeConstantsAtRuntime() throws Exception {
			final SeekBarPreference slider = inflateSlider(R.string._pref_key_fast_drain_reminder_minutes);

			assertNotNull("fast-drain reminder slider missing from pref_alerts.xml", slider);
			assertEquals(AppPrefs.MIN_FAST_DRAIN_REMINDER_MINUTES, slider.getMin());
			assertEquals(AppPrefs.MAX_FAST_DRAIN_REMINDER_MINUTES, slider.getMax());
		}

		@Test
		public void xmlFastDrainDefaults_matchTheFacadeConstants() throws Exception {
			final Integer xmlSustained = xmlDefaultOf(R.string._pref_key_fast_drain_sustained_minutes);
			final Integer xmlReminder = xmlDefaultOf(R.string._pref_key_fast_drain_reminder_minutes);

			assertNotNull("defaultValue attr missing from the fast-drain sustained slider", xmlSustained);
			assertNotNull("defaultValue attr missing from the fast-drain reminder slider", xmlReminder);
			assertEquals(AppPrefs.DEFAULT_FAST_DRAIN_SUSTAINED_MINUTES, (int) xmlSustained);
			assertEquals(AppPrefs.DEFAULT_FAST_DRAIN_REMINDER_MINUTES, (int) xmlReminder);
		}

		/**
		 * Both fast-drain timing sliders grey out with the fast-drain alert itself — the same "no settings that cannot do anything" rule the unplug reminder
		 * follows, and the reason neither slider needs a separate enabled check when it is read.
		 */
		@Test
		public void fastDrainTimingControls_greyOutWithTheAlertTheyTune() throws Exception {
			assertEquals(R.string._pref_key_notify_fast_drain, xmlDependencyOf(R.string._pref_key_fast_drain_sustained_minutes));
			assertEquals(R.string._pref_key_notify_fast_drain, xmlDependencyOf(R.string._pref_key_fast_drain_reminder_minutes));
		}

		/**
		 * Builds a slider from {@code pref_alerts.xml}, by preference key, exactly as the preference framework would — so its bounds are the ones a user's
		 * finger actually meets.
		 *
		 * @param keyRes the slider's preference-key string resource
		 *
		 * @return the inflated slider, or {@code null} when the screen no longer declares one
		 */
		private SeekBarPreference inflateSlider(int keyRes) throws Exception {
			final XmlResourceParser parser = context.getResources().getXml(R.xml.pref_alerts);
			try {
				for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
					if (event == XmlPullParser.START_TAG && hasKey(parser, keyRes)) {
						return new SeekBarPreference(context, Xml.asAttributeSet(parser));
					}
				}
			} finally {
				parser.close();
			}
			return null;
		}

		/**
		 * The {@code android:defaultValue} the framework reads for a slider, straight off the attribute — that <em>is</em> where the framework reads it from,
		 * so it is the one value {@link AppPrefs} cannot own and the one that has to be compared as text.
		 *
		 * @param keyRes the slider's preference-key string resource
		 *
		 * @return the declared default, or {@code null} when the slider or its attribute is gone
		 */
		private Integer xmlDefaultOf(int keyRes) throws Exception {
			return attrOf(R.xml.pref_alerts, keyRes, android.R.attr.defaultValue, (parser, i) -> parser.getAttributeIntValue(i, Integer.MIN_VALUE), null);
		}

		/**
		 * The same, for a preference whose {@code android:defaultValue} is a string. Reads a {@code @string/…} reference through to its text so the guard holds
		 * whichever form the attribute is written in — the convention is the reference, but a literal would still have to match.
		 *
		 * @param screenRes the preference screen declaring it
		 * @param keyRes    the preference's own key string resource
		 *
		 * @return the declared default as text, or {@code null} when the preference or its attribute is gone
		 */
		private String xmlStringDefaultOf(int screenRes, int keyRes) throws Exception {
			return attrOf(screenRes, keyRes, android.R.attr.defaultValue, (parser, i) -> {
				final int ref = parser.getAttributeResourceValue(i, 0);
				return ref == 0 ? parser.getAttributeValue(i) : context.getString(ref);
			}, null);
		}

		/**
		 * The {@code android:dependency} a preference declares, as the string resource it points at.
		 *
		 * @param keyRes the preference's own key string resource
		 *
		 * @return the dependency's key resource, or {@code 0} when the preference declares none
		 */
		private int xmlDependencyOf(int keyRes) throws Exception {
			return attrOf(R.xml.pref_alerts, keyRes, android.R.attr.dependency, (parser, i) -> parser.getAttributeResourceValue(i, 0), 0);
		}

		/**
		 * One attribute off the preference declared with {@code keyRes}, read straight from the given preference screen. The typed readers above differ only in
		 * which screen and attribute they want and how it is decoded, so the walk itself lives here once.
		 * <p>
		 * The first tag carrying the key wins, matching {@link #inflateSlider}: a key declared twice is a bug in the screen, not a value to pick between.
		 *
		 * @param screenRes the preference screen to walk
		 * @param keyRes    the preference's own key string resource
		 * @param attrRes   the {@code android.R.attr} constant to look for
		 * @param reader    decodes the attribute at the given index off the positioned parser
		 * @param absent    what the preference or the attribute being undeclared reads as
		 * @param <T>       the decoded attribute type
		 *
		 * @return the decoded attribute, or {@code absent}
		 */
		private <T> T attrOf(int screenRes, int keyRes, int attrRes, AttrReader<T> reader, T absent) throws Exception {
			final XmlResourceParser parser = context.getResources().getXml(screenRes);
			try {
				for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
					if (event != XmlPullParser.START_TAG || !hasKey(parser, keyRes)) {
						continue;
					}
					for (int i = 0; i < parser.getAttributeCount(); i++) {
						if (parser.getAttributeNameResource(i) == attrRes) {
							return reader.read(parser, i);
						}
					}
					return absent;
				}
			} finally {
				parser.close();
			}
			return absent;
		}

		/**
		 * Whether the tag the parser is on carries the given preference key. Preferences are found by key rather than by position — the screen grows and the
		 * categories get reordered.
		 */
		private boolean hasKey(XmlResourceParser parser, int keyRes) {
			for (int i = 0; i < parser.getAttributeCount(); i++) {
				if (parser.getAttributeNameResource(i) == android.R.attr.key
						&& parser.getAttributeResourceValue(i, 0) == keyRes) {
					return true;
				}
			}
			return false;
		}

		/** Decodes one attribute off a parser already positioned on the tag that declares it. */
		private interface AttrReader<T> {

			T read(XmlResourceParser parser, int index);
		}
	}

	/**
	 * {@link AppPrefs#clampChargeTarget}: the same guarantee for the charge target (#263). Both directions matter — a stored 0 is what a downgraded or restored
	 * preferences file yields, and without the lower clamp it would fire the "almost full" alert at empty.
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
	 * {@link AppPrefs#clampMinutesToMs}: the shared guarantee behind every timing preference — the fast-drain window and reminder (#109) and the unplug reminder
	 * (#264). Moved here with the helper itself, which the sliders' ranges are all enforced through.
	 */
	@RunWith(Parameterized.class)
	public static class ClampMinutesToMs {

		private static final long MINUTE_MS = 60_000L;

		@Parameter(0) public int stored;
		@Parameter(1) public int minMinutes;
		@Parameter(2) public int maxMinutes;
		@Parameter(3) public long expectedMs;

		@Parameters(name = "clampMinutesToMs({0}, {1}, {2}) = {3}ms")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{
					{5, 1, 30, 5 * MINUTE_MS},      // in range: unchanged
					{1, 1, 30, 1 * MINUTE_MS},      // boundaries kept
					{60, 5, 60, 60 * MINUTE_MS},
					// 0 sustained minutes would fire on the first above-limit tick — the spike alarm the design forbids; 0 reminder minutes would remind on
					// every broadcast.
					{0, 1, 30, 1 * MINUTE_MS},
					{-7, 1, 30, 1 * MINUTE_MS},
					{999, 1, 30, 30 * MINUTE_MS},
					{0, 5, 60, 5 * MINUTE_MS},
			});
		}

		@Test
		public void matchesExpected() {
			assertEquals(expectedMs, AppPrefs.clampMinutesToMs(stored, minMinutes, maxMinutes));
		}
	}

	/**
	 * {@link AppPrefs#reArmLevel} and {@link AppPrefs#targetIsAFullCharge}: the two derivations the alert engine, the notification copy and the temperature
	 * range all share (#263).
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

	/** The stored-value to night-mode mapping, including the unrecognised values that must fall back to following the system (#332). */
	@RunWith(Parameterized.class)
	public static class ThemeModeOf {

		@Parameter public String stored;
		@Parameter(1) public int expected;

		@Parameters(name = "themeModeOf({0}) = {1}")
		public static Collection<Object[]> data() {
			return Arrays.asList(new Object[][]{
					{AppPrefs.THEME_SYSTEM, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM},
					{AppPrefs.THEME_LIGHT, AppCompatDelegate.MODE_NIGHT_NO},
					{AppPrefs.THEME_DARK, AppCompatDelegate.MODE_NIGHT_YES},
					{null, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM},   // never set
					{"", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM},     // corrupt
					{"Dark", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM}, // the values are case-sensitive
			});
		}

		@Test
		public void matchesExpected() {
			assertEquals(expected, AppPrefs.themeModeOf(stored));
		}
	}
}
