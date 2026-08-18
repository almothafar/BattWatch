package com.almothafar.simplebatterynotifier;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * Every quantity written <em>literally</em> into the Arabic catalogue, laid out and checked against how it was written (#275).
 * <p>
 * The rest of the #275 work is about values the code interpolates, and {@link com.almothafar.simplebatterynotifier.util.BidiText#isolate} handles those at the
 * call site. It cannot reach a "100%" or a "0–300" typed straight into {@code values-ar}, and those reorder for exactly the same reason: the digits are a
 * left-to-right island in right-to-left prose and the {@code '%'}, the {@code '+'} and the en dash between a range's ends are neutrals that drift to the far
 * side of the number. Those are isolated in the resource itself, with {@code \\u2066}/{@code \\u2069} around each quantity.
 * <p>
 * This sweeps the whole catalogue rather than the seven strings that needed fixing, because the failure is invisible in review — the marks are zero-width, the
 * words read correctly in any editor, and a translator adding a sentence has no reason to know the rule exists. A new "80%" typed into any Arabic string fails
 * here, which is the only place it can be caught before a user sees it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "ar-rEG")
public class ArabicLiteralQuantityTest {

	/**
	 * Format placeholders — {@code %1$s}, {@code %d} — and the escaped {@code %%}. The first kind is taken out of the way before scanning: its digits are a
	 * placeholder index rather than a quantity, and the value that replaces it arrives already isolated. The second is not a placeholder at all once the string
	 * is on screen, so it collapses to the single {@code '%'} the reader sees.
	 */
	private static final Pattern PLACEHOLDER = Pattern.compile("%%|%\\d+\\$[a-zA-Z]|%[a-zA-Z]");

	/** A quantity as a reader sees it: digits, optionally a decimal point or an en-dashed range, optionally a trailing {@code '%'} or {@code '+'}. */
	private static final Pattern QUANTITY = Pattern.compile("\\d+(?:[.\u2013-]\\d+)*(?:\\s*[%+])?");

	private Context context;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();

		// Premise: values-ar is really what is loaded. Under the default locale every assertion below passes trivially — nothing reorders in English.
		assertEquals("ar-rEG should be the default locale", "ar", Locale.getDefault().getLanguage());
	}

	@Test
	public void everyLiteralQuantityInTheCatalogueRendersAsWritten() throws IllegalAccessException {
		final List<String> reordered = new ArrayList<>();

		for (Field field : R.string.class.getFields()) {
			final String text = context.getString(field.getInt(null));
			final String visual = BidiVisualOrder.inRtlParagraph(text);

			for (String quantity : quantitiesIn(text)) {
				if (!visual.contains(quantity)) {
					reordered.add(field.getName() + " → \"" + quantity + "\" does not survive layout");
				}
			}
		}

		// Named individually rather than counted, so a failure says which string a translator has to fix and what in it moved.
		assertEquals("literal quantities that reorder under Arabic; wrap each in \\u2066…\\u2069 in values-ar", List.of(), reordered);
	}

	/**
	 * The distinct quantities a reader would see in {@code text}, with format placeholders taken out of the way first.
	 *
	 * @param text one resource string, as the catalogue declares it
	 *
	 * @return each quantity once, in the order it appears
	 */
	private Set<String> quantitiesIn(String text) {
		final Set<String> found = new LinkedHashSet<>();
		final Matcher quantity = QUANTITY.matcher(PLACEHOLDER.matcher(text).replaceAll(match -> "%%".equals(match.group()) ? "%" : " "));
		while (quantity.find()) {
			found.add(quantity.group());
		}
		return found;
	}
}
