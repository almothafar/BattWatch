package com.almothafar.simplebatterynotifier.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.LocaleManagerCompat;
import androidx.core.os.LocaleListCompat;

/**
 * The language chosen in Settings → App language (#48), for the text built where no screen is: the notifications and the charge toast (#381).
 * <p>
 * From Android 13 the system applies the choice to the whole app, so a service's or a receiver's context already speaks it. Below 13,
 * {@link AppCompatDelegate#setApplicationLocales} reaches only AppCompat's screens: each one it opens is worded in the choice and sets the process's default
 * locale to it, and nothing does either for the monitoring service or the receivers, which kept the phone's language.
 * <p>
 * The choice is read from where AppCompat stores it ({@code autoStoreLocales} in the manifest), through {@link LocaleManagerCompat#getApplicationLocales}, its
 * public reader. {@link AppCompatDelegate#getApplicationLocales} would not do: it stays empty until a screen has opened in the process, and the service often
 * runs in one that never has — it starts on boot, and the system restarts it in a fresh process.
 */
public final class AppLanguage {

	private AppLanguage() {
		// Utility class - prevent instantiation
	}

	/**
	 * Apply the app's language to text about to be built: below Android 13, a context whose resources speak it, and the process's default locale set to it, as
	 * AppCompat sets it for each screen. The default decides whether {@link BidiText#isolate} wraps a number, which Arabic copy needs (#275).
	 * <p>
	 * The default is set on each call rather than once at start, because the framework puts it back: on every configuration change the process receives, a
	 * dark-mode switch say, {@code ActivityThread} resets it to the phone's locales.
	 * <p>
	 * The choice is read on each call too, not cached: it is a one-line file, and the status notification, rebuilt on every battery broadcast, already asks
	 * the system to re-create every notification channel each time, which costs far more. A cache would also need the language picker to keep it current.
	 *
	 * @param context the caller's context
	 * @return a context in the app's language; the same context from Android 13, or when the language is left on System
	 */
	// What lint asks for is done: app/build.gradle turns off Play's splitting by language, so every install has the Arabic strings, and the built bundle's
	// BundleConfig carries the setting. Lint does not read it from the Groovy DSL, so it reports the locale change all the same.
	@SuppressLint("AppBundleLocaleChanges")
	public static Context applyTo(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return context;
		}
		final LocaleListCompat chosen = LocaleManagerCompat.getApplicationLocales(context);
		if (chosen.isEmpty()) {
			return context;
		}
		final LocaleList locales = LocaleList.forLanguageTags(chosen.toLanguageTags());
		LocaleList.setDefault(locales);
		final Configuration configuration = new Configuration(context.getResources().getConfiguration());
		configuration.setLocales(locales);
		return context.createConfigurationContext(configuration);
	}
}
