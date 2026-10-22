package com.almothafar.simplebatterynotifier.ui.fragment;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.almothafar.simplebatterynotifier.R;
import com.almothafar.simplebatterynotifier.ui.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static java.util.Objects.isNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * How the notification-details picker's rows share their width on a narrow phone (#358). Measured with real fonts, which the other picker tests do without:
 * the class runs under Robolectric's native graphics, where a word that does not fit really wraps.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 360dp is the width of the narrowest phones in common use, the Mate 10 Pro among them.
@Config(sdk = 34, qualifiers = "w360dp-h800dp-xhdpi")
public class NotificationDetailsRowWidthTest {

	@Test
	@SuppressWarnings("deprecation")
	public void aFamilyName_isNeverBrokenInsideAWord_onANarrowPhone() {
		// The name shares the row with a checkbox, the live value and the drag handle. A name too long for its share may wrap between its words, but a word cut
		// in two ("Temperatur" over "e") reads as a broken screen.
		final Context context = ApplicationProvider.getApplicationContext();
		shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
		context.sendStickyBroadcast(battery());
		final ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup();
		controller.get().getSupportFragmentManager().beginTransaction().replace(R.id.settings_container, new NotificationDetailsFragment()).commitNow();
		shadowOf(Looper.getMainLooper()).idle();

		final RecyclerView list = controller.get().findViewById(R.id.notification_details_list);
		int names = 0;
		for (int index = 0; index < list.getChildCount(); index++) {
			final View row = list.getChildAt(index);
			final TextView name = row.findViewById(R.id.detail_label);
			if (isNull(name)) {
				continue;
			}
			names++;
			final Layout lines = name.getLayout();
			for (int line = 0; line < lines.getLineCount() - 1; line++) {
				final int end = lines.getLineEnd(line);
				assertTrue("\"" + name.getText() + "\" breaks inside a word", Character.isWhitespace(name.getText().charAt(end - 1)));
			}
		}
		assertEquals("a family's row is not laid out", 5, names);
		controller.close();
	}

	/** A plugged-out battery at 85%, so the rows carry their live values and share the width the way they do on a phone. */
	private static Intent battery() {
		final Intent battery = new Intent(Intent.ACTION_BATTERY_CHANGED);
		battery.putExtra(BatteryManager.EXTRA_LEVEL, 85);
		battery.putExtra(BatteryManager.EXTRA_SCALE, 100);
		battery.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING);
		battery.putExtra(BatteryManager.EXTRA_PLUGGED, 0);
		battery.putExtra(BatteryManager.EXTRA_TEMPERATURE, 346);
		battery.putExtra(BatteryManager.EXTRA_PRESENT, true);
		return battery;
	}
}
