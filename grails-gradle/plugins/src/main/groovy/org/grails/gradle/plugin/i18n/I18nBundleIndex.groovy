/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.i18n

import groovy.transform.CompileStatic

import org.gradle.api.InvalidUserDataException

/**
 * The set of message-bundle base names and locales a Grails artifact contributes, resolved once at
 * build time from the file names under {@code grails-app/i18n}.
 *
 * <p>Spring Boot's {@code ResourceBundleMessageSource} is configured with base names, not files, so
 * the split between a base name and its locale suffix has to happen somewhere. Doing it here means
 * malformed names fail the build, no runtime consumer re-parses file names, and the base-name list,
 * the available-locale list and the native-image resource hints cannot drift apart.</p>
 *
 * <h2>Why the split needs a stated convention</h2>
 *
 * <p>Splitting at the first underscore is wrong: applications may use any base name, so
 * {@code api_errors.properties} would be misread as base name {@code api} with the malformed locale
 * {@code errors}. But file names alone are genuinely ambiguous — {@code api_fr.properties} could be
 * base name {@code api} in French, or an unsuffixed base name {@code api_fr}. Unrestricted base
 * names, automatic locale inference and rejection of every malformed suffix cannot all hold at once,
 * so the convention is stated rather than guessed:</p>
 *
 * <blockquote>A trailing <em>valid</em> locale identifier is a locale suffix. Application base names
 * are otherwise unrestricted, but base names ending in a valid locale identifier are reserved,
 * because they are ambiguous under Java {@link java.util.ResourceBundle} conventions.</blockquote>
 *
 * <p>Where that convention gets it wrong, declare the base name explicitly:</p>
 *
 * <pre><code>grails {
 *     i18n {
 *         basenames = ['api', 'api_errors']
 *     }
 * }</code></pre>
 *
 * <p>Worked examples:</p>
 *
 * <table>
 * <caption>Inference outcomes</caption>
 * <tr><th>Files</th><th>Result</th></tr>
 * <tr><td>{@code messages}, {@code messages_de}</td><td>base name {@code messages}, locale {@code de}</td></tr>
 * <tr><td>{@code api_errors}, {@code api_errors_fr}</td><td>base name {@code api_errors}, locale {@code fr}</td></tr>
 * <tr><td>{@code api_fr} alone</td><td>base name {@code api}, locale {@code fr} — then fails, no {@code api.properties}</td></tr>
 * <tr><td>{@code messages}, {@code messages_dee}</td><td>build failure — {@code dee} looks like a mistyped locale</td></tr>
 * <tr><td>{@code api}, {@code api_errors}</td><td>build failure unless {@code api_errors} is declared</td></tr>
 * </table>
 *
 * @since 8.0
 */
@CompileStatic
final class I18nBundleIndex implements Serializable {

    private static final long serialVersionUID = 1L

    /** Bundle file extension; the only extension {@code ResourceBundleMessageSource} reads from the classpath. */
    static final String PROPERTIES_SUFFIX = '.properties'

    /**
     * Appended to every failure so the reader can find the full rationale rather than re-deriving the
     * naming rules from a one-line error.
     */
    static final String UPGRADE_REFERENCE =
            "See 'Message Bundles Resolved by Spring Boot' in the Grails 8 upgrade guide."

    private static final Set<String> ISO_LANGUAGES = Locale.getISOLanguages().toList().toSet().asImmutable()

    private static final Set<String> ISO_COUNTRIES = Locale.getISOCountries().toList().toSet().asImmutable()

    /** Base names, in stable alphabetical order. */
    final List<String> basenames

    /** Locale identifiers ({@code de}, {@code pt_BR}), in stable alphabetical order. */
    final List<String> locales

    private I18nBundleIndex(List<String> basenames, List<String> locales) {
        this.basenames = basenames.asImmutable()
        this.locales = locales.asImmutable()
    }

    /**
     * Resolves the index for one artifact.
     *
     * @param fileNames bundle file names, with or without the {@code .properties} extension
     * @param declaredBasenames base names declared through {@code grails { i18n { basenames } }}
     * @return the resolved index; empty when the artifact ships no bundles
     * @throws InvalidUserDataException when a name cannot be classified unambiguously, or a bundle
     *         has no locale-independent file (without which Spring Boot's message-source
     *         auto-configuration does not activate at all)
     */
    static I18nBundleIndex from(Collection<String> fileNames, Collection<String> declaredBasenames) {
        Set<String> declared = declaredBasenames == null ? [] as Set<String> : declaredBasenames.toSet()
        List<String> stems = fileNames.collect { String name ->
            name.endsWith(PROPERTIES_SUFFIX) ? name.substring(0, name.length() - PROPERTIES_SUFFIX.length()) : name
        }.unique()

        if (!stems) {
            return new I18nBundleIndex([], [])
        }

        Set<String> basenames = new LinkedHashSet<>()
        Set<String> locales = new TreeSet<>()

        // Shortest first, so a base name is always classified before its locale variants.
        // Length then alphabetical, so the outcome never depends on file-system ordering.
        List<String> ordered = stems.sort(false) { String a, String b ->
            a.length() <=> b.length() ?: a <=> b
        }
        for (String stem : ordered) {
            if (declared.contains(stem)) {
                basenames << stem
                continue
            }

            String owner = longestBasenamePrefix(stem, basenames)
            if (owner != null) {
                String suffix = stem.substring(owner.length() + 1)
                if (!validLocale(suffix)) {
                    throw new InvalidUserDataException("""\
Cannot classify i18n bundle '${stem}${PROPERTIES_SUFFIX}'. It reads as base name '${owner}' with the \
locale suffix '${suffix}', but '${suffix}' is not a valid locale (expected language(_COUNTRY(_variant)) \
using ISO codes).
If '${suffix}' is a mistyped locale, correct it. If '${stem}' is genuinely a separate base name, declare it:
    grails { i18n { basenames = ['${owner}', '${stem}'] } }
${UPGRADE_REFERENCE}""")
                }
                locales << suffix
                continue
            }

            String impliedBase = basenameOfLocaleVariant(stem)
            if (impliedBase != null) {
                basenames << impliedBase
                locales << stem.substring(impliedBase.length() + 1)
                continue
            }

            basenames << stem
        }

        List<String> sortedBasenames = basenames.toList().sort()
        requireLocaleIndependentBundle(sortedBasenames, stems.toSet())
        new I18nBundleIndex(sortedBasenames, locales.toList())
    }

    /** {@code true} when this artifact contributes no message bundles at all. */
    boolean isEmpty() {
        basenames.isEmpty()
    }

    /**
     * The longest already-classified base name {@code b} for which {@code stem} is {@code b_something},
     * or {@code null} when the stem is not a variant of any known base name. Longest wins so that
     * {@code api-errors_fr} attaches to {@code api-errors} rather than to {@code api}.
     */
    private static String longestBasenamePrefix(String stem, Set<String> basenames) {
        basenames.findAll { String base -> stem.startsWith(base + '_') }
                .max { String base -> base.length() }
    }

    /**
     * The base name implied by a stem that ends in a valid locale suffix, or {@code null} when the
     * stem carries no valid locale suffix and is therefore a base name in its own right.
     */
    private static String basenameOfLocaleVariant(String stem) {
        int underscore = stem.indexOf('_')
        while (underscore > 0) {
            if (validLocale(stem.substring(underscore + 1))) {
                return stem.substring(0, underscore)
            }
            underscore = stem.indexOf('_', underscore + 1)
        }
        null
    }

    /**
     * Whether a suffix follows the {@link java.util.ResourceBundle} convention
     * {@code language(_COUNTRY(_variant))} — deliberately not BCP 47, so {@code foo_en_prod} is
     * rejected rather than misparsed as a script or region.
     */
    private static boolean validLocale(String suffix) {
        String[] parts = suffix.split('_', 3)
        if (!ISO_LANGUAGES.contains(parts[0])) {
            return false
        }
        parts.length == 1 || ISO_COUNTRIES.contains(parts[1])
    }

    /**
     * Spring Boot's {@code MessageSourceAutoConfiguration} only activates when a locale-independent
     * bundle exists for a configured base name, and it contributes the whole {@code messageSource}
     * bean. A bundle shipping only locale variants would therefore leave the application with no
     * message source at all, so it fails here instead.
     */
    private static void requireLocaleIndependentBundle(List<String> basenames, Set<String> stems) {
        List<String> missing = basenames.findAll { String base -> !stems.contains(base) }
        if (missing) {
            throw new InvalidUserDataException("""\
No locale-independent bundle for ${missing.collect { "'${it}${PROPERTIES_SUFFIX}'" }.join(', ')}. \
Spring Boot's message-source auto-configuration requires a base bundle without a locale suffix, and \
backs off entirely without one — leaving the application with no messageSource bean.
Add the missing file, or rename the locale-specific bundle if its base name was not what you intended.
${UPGRADE_REFERENCE}""")
        }
    }
}
