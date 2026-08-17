package com.almothafar.simplebatterynotifier;

import org.junit.Test;
import org.w3c.dom.Document;
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
 * {@link android.content.res.Resources#getString(int, Object...)} formats with the <b>configuration</b> locale, so a numeric conversion in a resource renders
 * {@code ٤٠} on any region-bearing Arabic locale — with no {@code String.format} call anywhere in our code to give the game away.
 * {@link android.content.res.Resources#getQuantityString(int, int, Object...)} does the same for {@code <plurals>}. The rule the guidelines state is that
 * every number a user sees is Western in every locale, which leaves a resource no legitimate numeric conversion at all: the number is formatted in code,
 * through {@link com.almothafar.simplebatterynotifier.util.BatteryPercentFormatter} or {@code String.valueOf}, and interpolated through a {@code %s}.
 * <p>
 * So the check is stated the way the rule is: <b>{@code %s} and {@code %%} are the only conversions a resource may contain.</b> Enumerating the numeric ones
 * instead would have to enumerate their flags, widths and precisions too — {@code %,d} and {@code %02d} localise digits exactly like {@code %d}, and
 * {@code %02d} is already an idiom in this project's time formatting — and the first one missed is the next recurrence.
 * <p>
 * That makes this a static property of the catalogue, so it is checked as one. The behavioural tests that pin individual call sites live beside the code they
 * cover; this one exists so the thirteenth occurrence fails in CI the day it is written, in whichever locale file it appears, instead of surviving three
 * releases the way the last batch did.
 */
public class StringResourceDigitsTest {

	/**
	 * Any format conversion: {@code %s}, {@code %1$d}, {@code %,d}, {@code %02d}, {@code %.2f}, {@code %1$-5s}, {@code %tY}. Deliberately matches the whole
	 * conversion grammar — argument index, flags, width, precision — rather than a list of numeric conversions, so that widening the check to a new one is not
	 * required. Escaped percents are removed before this runs, so {@code %%} never reaches it.
	 * <p>
	 * One conversion is deliberately <b>not</b> matched: the space flag, {@code "% d"}. It is indistinguishable from ordinary prose — this catalogue alone
	 * contains "between 20% and 80% day to day", "collapses near the bottom (e.g. 20% behaves like 2%)" and three more, each of which parses as a space-flagged
	 * conversion and none of which is one. Nobody writes {@code "% d"} in a string resource; several people have already written "80% and", so the flag class
	 * excludes the space and the check keeps its reach over the conversions that actually occur.
	 */
	private static final Pattern CONVERSION = Pattern.compile("%(\\d+\\$)?([-#+0,(]*)(\\d+)?(\\.\\d+)?([a-zA-Z])");

	/** Elements whose text is a translatable string body: {@code <string>} plus the {@code <item>} children of {@code <plurals>} and {@code <string-array>}. */
	private static final List<String> TEXT_ELEMENTS = List.of("string", "item");

	private static final DocumentBuilderFactory FACTORY = namespaceAwareFactory();

	@Test
	public void noStringResourceUsesANumericConversion() throws Exception {
		final List<File> files = resourceFiles();
		assertTrue("found no values*/*.xml to scan — the test is not looking where it thinks it is", !files.isEmpty());

		final DocumentBuilder builder = FACTORY.newDocumentBuilder();
		final List<String> offenders = new ArrayList<>();
		for (File file : files) {
			collectOffenders(builder, file, offenders);
		}

		if (!offenders.isEmpty()) {
			fail("Numeric conversions localise their digits under ar-EG/ar-SA/ar-JO — format the number in code and interpolate it with %s instead:\n  "
					+ String.join("\n  ", offenders));
		}
	}

	/**
	 * Records every string body in one file that carries a conversion other than {@code %s}.
	 *
	 * @param builder   the shared parser
	 * @param file      the resource file to scan
	 * @param offenders accumulator of {@code "values-ar/strings.xml: name -> body"} descriptions
	 */
	private static void collectOffenders(DocumentBuilder builder, File file, List<String> offenders) throws Exception {
		final Document document = builder.parse(file);
		for (String tag : TEXT_ELEMENTS) {
			final NodeList nodes = document.getElementsByTagName(tag);
			for (int i = 0; i < nodes.getLength(); i++) {
				final Element element = (Element) nodes.item(i);
				// No exemption for formatted="false", which is the same trap as translatable="false" one attribute over: it silences aapt2 at build time and
				// changes nothing at runtime, so getString(id, args) still hands the body to String.format under the configuration locale and a %1$d in one
				// still renders ٩٠ on ar-EG. Four strings here carry the attribute; excusing them would leave the catalogue's longest prose unguarded.
				// getTextContent, not the raw source: comments and entities are already resolved away, so a %1$d written in an explanatory comment (this fix
				// left several) can't be mistaken for one in a string. Escaped percents go first, so %%d reads as a literal '%' followed by 'd'.
				final String body = element.getTextContent().replace("%%", "");
				final Matcher matcher = CONVERSION.matcher(body);
				while (matcher.find()) {
					final String conversion = matcher.group(5);
					if (!"s".equals(conversion) && !"S".equals(conversion)) {
						offenders.add(file.getParentFile().getName() + "/" + file.getName() + ": <" + tag + "> " + describe(element)
								+ " -> " + matcher.group() + " in " + element.getTextContent().trim());
					}
				}
			}
		}
	}

	/**
	 * A name for the offending element. {@code <item>} carries no name of its own, so its parent's is used.
	 *
	 * @param element the offending element
	 *
	 * @return the resource name to report
	 */
	private static String describe(Element element) {
		final String name = element.getAttribute("name");
		if (!name.isEmpty()) {
			return name;
		}
		return element.getParentNode() instanceof Element parent ? parent.getAttribute("name") : "(unnamed)";
	}

	/**
	 * @return a factory that tolerates the {@code xmlns:tools} prefixes the resource files declare
	 */
	private static DocumentBuilderFactory namespaceAwareFactory() {
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory;
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
	 * Locates this module's {@code res}. Two working directories occur in practice and both are one step away: Gradle runs tests from the module directory,
	 * IDEs often from the repository root. Anything else is a misconfiguration worth failing on rather than searching past — walking further up could only
	 * find the resources of some unrelated project containing this checkout.
	 *
	 * @return the resource root
	 */
	private static File resourceRoot() {
		final File cwd = new File("").getAbsoluteFile();
		final File fromModule = new File(cwd, "src/main/res");
		if (fromModule.isDirectory()) {
			return fromModule;
		}
		final File fromRepoRoot = new File(cwd, "app/src/main/res");
		if (fromRepoRoot.isDirectory()) {
			return fromRepoRoot;
		}
		throw new IllegalStateException("expected src/main/res or app/src/main/res under " + cwd);
	}
}
