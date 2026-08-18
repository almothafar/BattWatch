package com.almothafar.simplebatterynotifier;

import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

/**
 * Lays a string out the way a right-to-left reader actually sees it, so a test can assert on visual order rather than on content (#275).
 * <p>
 * This closes the gap the issue named: {@code assertTrue(text.contains("32.0 °C"))} passes on copy that reaches an Arabic user as {@code C° 32.0}, because
 * reordering is a property of the layout and not of the characters — every content assertion in this suite is blind to it. {@link Bidi} is the JDK's
 * implementation of UAX&nbsp;#9, the same algorithm Android lays text out with, so running it over the string the production code produced catches the
 * reordering that otherwise only a screenshot would.
 * <p>
 * Not a substitute for looking at a device — font shaping, the notification shade's own text direction and a translator's later edit all live outside it — but
 * it does pin the one property that regressed here, in CI, on every surface at once.
 */
public final class BidiVisualOrder {

	/** The marks and embedding controls {@code BidiFormatter} inserts, plus the rest of the invisible bidi set: present in the string, never drawn. */
	private static final String BIDI_CONTROLS = "[‎‏‪-‮⁦-⁩]";

	private BidiVisualOrder() {
		// Utility class - prevent instantiation
	}

	/**
	 * The left-to-right sequence the characters of {@code logical} are drawn in, laid out as a right-to-left paragraph — an Arabic notification, in practice.
	 * <p>
	 * {@link Bidi#DIRECTION_DEFAULT_RIGHT_TO_LEFT} rather than a forced RTL paragraph, because that is Android's rule: a {@code TextView} resolves its
	 * paragraph direction from the first strong character and falls back to the layout direction, which under Arabic is RTL. Invisible controls are dropped
	 * from the result, so what comes back is what reaches the eye.
	 *
	 * @param logical the string as the production code assembled it
	 *
	 * @return its characters in the order they are drawn, left to right
	 */
	public static String inRtlParagraph(String logical) {
		final Bidi bidi = new Bidi(logical, Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
		final int runCount = bidi.getRunCount();
		final Run[] runs = new Run[runCount];
		final byte[] levels = new byte[runCount];
		for (int i = 0; i < runCount; i++) {
			runs[i] = new Run(logical.substring(bidi.getRunStart(i), bidi.getRunLimit(i)), (byte) bidi.getRunLevel(i));
			levels[i] = runs[i].level();
		}

		// reorderVisually places the runs; the characters inside a right-to-left run are still in logical order and have to be reversed themselves.
		Bidi.reorderVisually(levels, 0, runs, 0, runCount);

		final StringBuilder visual = new StringBuilder(logical.length());
		for (Run run : runs) {
			visual.append((run.level() & 1) == 1 ? reverseByGrapheme(run.text()) : run.text());
		}
		return stripControls(visual.toString());
	}

	/**
	 * {@code text} reversed a reader's character at a time rather than a {@code char} at a time.
	 * <p>
	 * {@link StringBuilder#reverse()} keeps surrogate pairs together but not combining sequences, and the Arabic catalogue is full of them — the shadda in
	 * "حدّك", the fathatan in "تقريباً". Reversing those by code unit puts the mark before the letter it sits on, so the helper would report a visual order no
	 * reader could ever see, and an assertion written against Arabic words rather than Latin quantities would fail for a reason that is not the one under test.
	 *
	 * @param text one right-to-left run, in logical order
	 *
	 * @return the same run with its grapheme clusters in the opposite order, each cluster left intact
	 */
	private static String reverseByGrapheme(String text) {
		final BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
		clusters.setText(text);

		final List<String> parts = new ArrayList<>();
		int start = clusters.first();
		for (int end = clusters.next(); end != BreakIterator.DONE; start = end, end = clusters.next()) {
			parts.add(text.substring(start, end));
		}

		final StringBuilder reversed = new StringBuilder(text.length());
		for (int i = parts.size() - 1; i >= 0; i--) {
			reversed.append(parts.get(i));
		}
		return reversed.toString();
	}

	/**
	 * {@code text} without its bidi controls — the characters that steer the layout but are never drawn. Lets an assertion on the <em>content</em> of isolated
	 * copy stay written as the words a reader sees.
	 *
	 * @param text a string that may carry isolation marks
	 *
	 * @return the same string with every bidi control removed
	 */
	public static String stripControls(String text) {
		return text.replaceAll(BIDI_CONTROLS, "");
	}

	/**
	 * Asserts that {@code quantity} survives right-to-left layout intact and in the order it was written — the property that fails when a {@code '%'},
	 * {@code '°'} or unit is left outside the isolated run and drifts to the far side of the number it belongs to.
	 *
	 * @param logical  the string the production code produced
	 * @param quantity the left-to-right run that must reach the reader unbroken, e.g. {@code "20%"} or {@code "32.0 °C"}
	 */
	public static void assertRendersAsWritten(String logical, String quantity) {
		final String visual = inRtlParagraph(logical);
		assertTrue("\"" + quantity + "\" does not survive RTL layout\n  logical: " + logical + "\n  visual : " + visual, visual.contains(quantity));
	}

	/** One directional run of the laid-out string: its text in logical order, and the embedding level that decides which way it is drawn. */
	private record Run(String text, byte level) {
	}
}
