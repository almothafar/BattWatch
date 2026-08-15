package com.almothafar.simplebatterynotifier;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Guards the whole string catalogue against the Eastern-digit defect (#96/#154/#241/#273) rather than the twelve strings that happened to carry it.
 * <p>
 * {@link android.content.res.Resources#getString(int, Object...)} formats with the <b>configuration</b> locale, so a {@code %d} placeholder in a resource
 * renders {@code ٤٠} on any region-bearing Arabic locale — with no {@code String.format} call anywhere in our code to give the game away. The rule the
 * guidelines state is that every number a user sees is Western in every locale, which makes {@code %d} in a string resource unusable full stop: the number is
 * formatted in code, through {@link com.almothafar.simplebatterynotifier.util.BatteryPercentFormatter} or {@code String.valueOf}, and interpolated through a
 * {@code %s}.
 * <p>
 * That makes this a static property of the catalogue, so it is checked as one. The behavioural tests that pin individual call sites live beside the code they
 * cover; this one exists so the thirteenth occurrence fails in CI the day it is written, in whichever locale file it appears, instead of surviving three
 * releases the way the last batch did.
 */
public class StringResourceDigitsTest {

	/** {@code %d}, {@code %1$d} and friends — every numeric conversion {@code getString} would localise. */
	private static final Pattern NUMERIC_PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[deEfgGoxX]");

	@Test
	public void noStringResourceUsesANumericPlaceholder() throws Exception {
		final List<File> files = resourceFiles();
		assertTrue("found no values*/*.xml to scan — the test is not looking where it thinks it is", !files.isEmpty());

		final List<String> offenders = new ArrayList<>();
		for (File file : files) {
			collectOffenders(file, offenders);
		}

		if (!offenders.isEmpty()) {
			fail("Numeric placeholders localise their digits under ar-EG/ar-SA/ar-JO — format the number in code and interpolate it with %s instead:\n  "
					+ String.join("\n  ", offenders));
		}
	}

	/**
	 * Records every {@code <string>} in one file whose body carries a numeric placeholder.
	 *
	 * @param file      the resource file to scan
	 * @param offenders accumulator of {@code "values-ar/strings.xml: name -> body"} descriptions
	 */
	private static void collectOffenders(File file, List<String> offenders) throws Exception {
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// The files declare xmlns:tools; without this the parser rejects the prefixed attributes it never resolves.
		factory.setNamespaceAware(true);
		final DocumentBuilder builder = factory.newDocumentBuilder();

		final NodeList strings = builder.parse(file).getElementsByTagName("string");
		for (int i = 0; i < strings.getLength(); i++) {
			final Element element = (Element) strings.item(i);
			// getTextContent, not the raw source: comments and entities are already resolved away, so a %1$d written in an explanatory
			// comment (this fix left several) can't be mistaken for one in a string.
			final String body = element.getTextContent();
			final Matcher matcher = NUMERIC_PLACEHOLDER.matcher(body);
			if (matcher.find()) {
				offenders.add(file.getParentFile().getName() + "/" + file.getName()
						+ ": " + element.getAttribute("name") + " -> " + body.trim());
			}
		}
	}

	/**
	 * Every XML file under a {@code values} directory in the main source set, so a new locale or a second strings file is covered the day it is added rather
	 * than when someone remembers to extend this list.
	 *
	 * @return the resource files to scan, sorted for a stable failure message
	 */
	private static List<File> resourceFiles() {
		final File res = resourceRoot();
		final File[] valuesDirs = res.listFiles(child -> child.isDirectory() && child.getName().startsWith("values"));
		if (valuesDirs == null) {
			return List.of();
		}

		final List<File> files = new ArrayList<>();
		for (File dir : valuesDirs) {
			final File[] xml = dir.listFiles(child -> child.getName().endsWith(".xml"));
			if (xml != null) {
				files.addAll(Arrays.asList(xml));
			}
		}
		files.sort(Comparator.comparing(File::getPath));
		return files;
	}

	/**
	 * Locates {@code src/main/res} by walking up from the working directory, which Gradle sets to the module dir but IDEs often set to the repository root.
	 *
	 * @return the resource root
	 */
	private static File resourceRoot() {
		File dir = new File("").getAbsoluteFile();
		while (dir != null) {
			final File candidate = new File(dir, "src/main/res");
			if (candidate.isDirectory()) {
				return candidate;
			}
			final File moduleCandidate = new File(dir, "app/src/main/res");
			if (moduleCandidate.isDirectory()) {
				return moduleCandidate;
			}
			dir = dir.getParentFile();
		}
		throw new IllegalStateException("could not locate src/main/res from " + new File("").getAbsolutePath());
	}
}
