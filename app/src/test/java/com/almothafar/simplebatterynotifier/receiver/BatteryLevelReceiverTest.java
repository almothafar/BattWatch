package com.almothafar.simplebatterynotifier.receiver;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.receiver.BatteryLevelReceiver.LevelAlertState;
import com.almothafar.simplebatterynotifier.service.AlertType;
import com.almothafar.simplebatterynotifier.service.NotificationService;
import com.almothafar.simplebatterynotifier.service.SystemService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Robolectric + Mockito tests for {@link BatteryLevelReceiver}'s threshold and de-duplication logic.
 * <p>
 * The receiver reads the delivered {@code ACTION_BATTERY_CHANGED} intent (#159) and delegates the
 * actual notification to {@link NotificationService}, whose static methods are mocked so we can
 * assert <em>which</em> alert (if any) it decides to send without doing real Android notification
 * work. {@link SystemService} is left real so it builds a {@code BatteryDO} from the intent we deliver.
 * <p>
 * Episode state lives in SharedPreferences (#164), and each {@code receive()} uses a fresh receiver
 * instance — so every multi-broadcast test also exercises the state surviving "process death"
 * (nothing is carried in memory between broadcasts).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BatteryLevelReceiverTest {

	private Context context;
	private Intent latestBattery;

	@Before
	public void setUp() {
		// Robolectric gives each test a fresh application (and thus fresh SharedPreferences), so the
		// persisted episode state (#164) always starts cleared; no manual reset needed.
		context = ApplicationProvider.getApplicationContext();
	}

	@Test
	public void discharging_belowCritical_sendsCriticalAlert() {
		publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 15, 100, 0);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.CRITICAL), anyInt(), anyBoolean()));
		}
	}

	@Test
	public void discharging_betweenCriticalAndWarning_sendsWarningAlert() {
		publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 35, 100, 0);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.WARNING), anyInt(), anyBoolean()));
		}
	}

	@Test
	public void discharging_aboveWarning_sendsNoAlert() {
		publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 100, 0);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.WARNING), anyInt(), anyBoolean()), never());
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.CRITICAL), anyInt(), anyBoolean()), never());
		}
	}

	@Test
	public void discharging_warningNotRepeatedWhileStillInWarningBand() {
		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 38, 100, 0);
			receive();
			// Still in the warning band, different level -> must not re-alert
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 35, 100, 0);
			receive();

			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.WARNING), anyInt(), anyBoolean()), times(1));
		}
	}

	@Test
	public void discharging_alertEveryTick_repeatsCriticalAlert() {
		enableAlertEveryTick();

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 15, 100, 0);
			receive();
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 14, 100, 0);
			receive();

			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.CRITICAL), anyInt(), anyBoolean()), times(2));
		}
	}

	@Test
	public void full_whileCharging_sendsFullAlertOnce() {
		// Simulate the battery already sitting at 100% (unchanged) so the charging/full branch runs
		saveLevelState(new LevelAlertState(100, null, false, false));
		publishBattery(BatteryManager.BATTERY_STATUS_FULL, 100, 100, BatteryManager.BATTERY_PLUGGED_AC);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();
			receive(); // second identical tick must not re-send
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), anyInt(), anyBoolean()), times(1));
		}
	}

	@Test
	public void unplug_reArmsFullAlert_forNextChargeSession() {
		saveLevelState(new LevelAlertState(100, null, false, false));
		publishBattery(BatteryManager.BATTERY_STATUS_FULL, 100, 100, BatteryManager.BATTERY_PLUGGED_AC);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();
			// Unplugged: the charge-session reset re-arms the full alert; plugging back in at full fires again.
			BatteryLevelReceiver.onChargerDisconnected(context);
			receive();

			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), anyInt(), anyBoolean()), times(2));
		}
	}

	@Test
	public void chargeTarget_alertsOnTheWayUpAndOnlyOnce() {
		// End-to-end for #263: the stored target, not a constant, is what the alert fires at — and the rest of the climb to a full charge stays quiet. The
		// target is deliberately NOT the default 90, so a hardcoded default (or a mistyped preference key) fails this instead of sliding through.
		setChargeTarget(85);
		saveLevelState(new LevelAlertState(84, null, false, false));

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			publishBattery(BatteryManager.BATTERY_STATUS_CHARGING, 85, 100, BatteryManager.BATTERY_PLUGGED_AC);
			receive();
			publishBattery(BatteryManager.BATTERY_STATUS_CHARGING, 95, 100, BatteryManager.BATTERY_PLUGGED_AC);
			receive();
			publishBattery(BatteryManager.BATTERY_STATUS_FULL, 100, 100, BatteryManager.BATTERY_PLUGGED_AC);
			receive();

			// Once, and carrying the level it actually fired at — that level is what keeps the copy honest.
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), eq(85), anyBoolean()), times(1));
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), anyInt(), anyBoolean()), times(1));
		}
	}

	@Test
	public void chargeTarget_atAnOemChargeCap_alertsFromTheNotChargingStatus() {
		// End-to-end for the cap case: the device holds at 88% reporting NOT_CHARGING with the cable in, and never reports FULL. Pins the intent → ChargeState
		// wiring, which is the half the decision tests can't see.
		setChargeTarget(85);
		saveLevelState(new LevelAlertState(88, null, false, false));

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			publishBattery(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 88, 100, BatteryManager.BATTERY_PLUGGED_AC);
			receive();
			receive(); // it sits there for hours; only the first tick may alert

			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), eq(88), anyBoolean()), times(1));
		}
	}

	@Test
	public void chargeTarget_whileDischarging_neverAlerts() {
		setChargeTarget(85);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			// The second broadcast repeats the level, which is the path that reaches the full-battery branch while discharging — the one place a bare "level ≥
			// target" would announce a charge that never happened.
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 85, 100, 0);
			receive();
			receive();

			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), anyInt(), anyBoolean()), never());
		}
	}

	@Test
	public void unplugReminder_offByDefault_leavesTheAlertAtOncePerCharge() {
		// The default the feature ships with (#264). The stored state is mid-charge with the alert long since posted — an install that never touches the switch
		// must still hear nothing more until the cable comes out.
		saveLevelState(new LevelAlertState(90, null, true, true, 1));
		publishBattery(BatteryManager.BATTERY_STATUS_CHARGING, 90, 100, BatteryManager.BATTERY_PLUGGED_AC);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), anyInt(), anyBoolean()), never());
		}
	}

	@Test
	public void unplugReminder_switchedOn_rePostsTheAlertAsAReminder() {
		// End-to-end for #264: the switch is read from the preference the settings screen writes, and a due reminder re-posts the full alert flagged as a
		// repeat — which is what stops the dispatcher silencing it with setOnlyAlertOnce.
		//
		// The stored alert time is 1 ms after the epoch, i.e. any real gap has elapsed by now; that keeps the assertion off the wall clock.
		setUnplugReminderEnabled(true);
		saveLevelState(new LevelAlertState(90, null, true, true, 1));
		publishBattery(BatteryManager.BATTERY_STATUS_CHARGING, 90, 100, BatteryManager.BATTERY_PLUGGED_AC);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();
			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), eq(90), eq(true)));
		}
	}

	@Test
	public void unplugReminder_switchedOn_stopsAtUnplug() {
		// The promise the feature is named for. Same due reminder, cable out: the alert is taken down instead of repeated.
		setUnplugReminderEnabled(true);
		saveLevelState(new LevelAlertState(90, null, true, true, 1));
		publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 90, 100, 0);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();

			ns.verify(() -> NotificationService.sendNotification(any(Context.class), eq(AlertType.FULL), anyInt(), anyBoolean()), never());
			ns.verify(() -> NotificationService.clearLevelAlert(any(Context.class)));
		}
	}

	@Test
	public void unplugAtFull_dismissesTheStaleAlertBeforePostingTheCriticalOne() {
		// The level alerts share one notification ID, so the dismissal has to run first: cancelling
		// after sending would wipe the critical alert this same broadcast just decided. Only the
		// receiver can pin this — the decision record carries no ordering.
		saveLevelState(new LevelAlertState(21, null, true, true));
		publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 15, 100, 0);

		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			receive();

			final InOrder ordered = inOrder(NotificationService.class);
			ordered.verify(ns, () -> NotificationService.clearLevelAlert(any(Context.class)));
			ordered.verify(ns, () -> NotificationService.sendNotification(any(Context.class), eq(AlertType.CRITICAL), anyInt(), anyBoolean()));
		}
	}

	@Test
	public void temperature_hotSpell_alertsOnceAcrossBroadcasts() {
		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			// 46.0 °C — above the default 45 °C threshold. Each receive() is a fresh receiver instance,
			// so the second broadcast staying hot is exactly the killed-and-restarted-mid-spell case.
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 100, 0, 460);
			receive();
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 79, 100, 0, 465);
			receive();

			ns.verify(() -> NotificationService.sendTemperatureNotification(any(Context.class), anyInt()), times(1));
		}
	}

	@Test
	public void temperature_cooledBelowHysteresis_alertsAgainOnNextSpell() {
		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 100, 0, 460);
			receive();
			// Cooled to 41.0 °C (≤ 45 − 3 hysteresis) — re-arms; the next spell alerts again.
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 79, 100, 0, 410);
			receive();
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 78, 100, 0, 460);
			receive();

			ns.verify(() -> NotificationService.sendTemperatureNotification(any(Context.class), anyInt()), times(2));
		}
	}

	@Test
	public void temperature_cooledBelowHysteresis_dismissesStaleAlert() {
		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 100, 0, 460);
			receive();
			// Cooled to 41.0 °C (≤ 45 − 3 hysteresis): the spell is over, so the shown warning goes too (#259).
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 79, 100, 0, 410);
			receive();

			ns.verify(() -> NotificationService.clearTemperatureAlert(any(Context.class)), times(1));
		}
	}

	@Test
	public void temperature_withinHysteresisBand_keepsAlertShown() {
		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 100, 0, 460);
			receive();
			// 43.0 °C — below the 45 °C threshold but not yet past the hysteresis, so the battery is still
			// hot enough for the warning to be true. Dismissing here would flap the notification.
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 79, 100, 0, 430);
			receive();

			ns.verify(() -> NotificationService.clearTemperatureAlert(any(Context.class)), never());
		}
	}

	@Test
	public void temperature_alertDisabledWhileShown_dismissesStaleAlert() {
		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			publishBattery(BatteryManager.BATTERY_STATUS_DISCHARGING, 80, 100, 0, 460);
			receive();
			// Switching the alert off is the other way a spell ends: the warning must not be left behind.
			setHighTemperatureAlertEnabled(false);
			receive();

			ns.verify(() -> NotificationService.clearTemperatureAlert(any(Context.class)), times(1));
		}
	}

	@Test
	public void unexpectedAction_isIgnored() {
		// The receiver reads the delivered intent (#159); a wrong-action intent (whose missing extras
		// would otherwise read as a 0% battery) must be dropped by the guard, not alerted on.
		try (MockedStatic<NotificationService> ns = mockStatic(NotificationService.class)) {
			new BatteryLevelReceiver().onReceive(context, new Intent(Intent.ACTION_POWER_CONNECTED));
			ns.verifyNoInteractions();
		}
	}

	// --- helpers -------------------------------------------------------------

	private void receive() {
		new BatteryLevelReceiver().onReceive(context, latestBattery);
	}

	private void publishBattery(final int status, final int level, final int scale, final int plugged) {
		publishBattery(status, level, scale, plugged, 250); // 25.0 °C, well below the alert threshold
	}

	/**
	 * Build the battery-changed intent {@link #receive()} will deliver. The receiver reads the
	 * delivered intent directly (#159), so no sticky broadcast is involved anymore.
	 */
	private void publishBattery(
			int status,
			int level,
			int scale,
			int plugged,
			int temperatureTenthsC) {
		final Intent battery = new Intent(Intent.ACTION_BATTERY_CHANGED);
		battery.putExtra(BatteryManager.EXTRA_STATUS, status);
		battery.putExtra(BatteryManager.EXTRA_LEVEL, level);
		battery.putExtra(BatteryManager.EXTRA_SCALE, scale);
		battery.putExtra(BatteryManager.EXTRA_PLUGGED, plugged);
		battery.putExtra(BatteryManager.EXTRA_PRESENT, true);
		battery.putExtra(BatteryManager.EXTRA_TEMPERATURE, temperatureTenthsC);
		latestBattery = battery;
	}

	private void enableAlertEveryTick() {
		PreferenceManager.getDefaultSharedPreferences(context)
				.edit()
				.putBoolean(context.getString(R.string._pref_key_notify_every_tick), true)
				.commit();
	}

	private void setUnplugReminderEnabled(boolean enabled) {
		PreferenceManager.getDefaultSharedPreferences(context)
				.edit()
				.putBoolean(context.getString(R.string._pref_key_notify_unplug_reminder), enabled)
				.commit();
	}

	private void setChargeTarget(int target) {
		PreferenceManager.getDefaultSharedPreferences(context)
				.edit()
				.putInt(context.getString(R.string._pref_key_charge_target), target)
				.commit();
	}

	private void setHighTemperatureAlertEnabled(boolean enabled) {
		PreferenceManager.getDefaultSharedPreferences(context)
				.edit()
				.putBoolean(context.getString(R.string._pref_key_notify_high_temperature), enabled)
				.commit();
	}

	private void saveLevelState(final LevelAlertState state) {
		BatteryLevelReceiver.saveLevelState(PreferenceManager.getDefaultSharedPreferences(context), state);
	}
}
