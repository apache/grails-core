/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.plugins.i18n;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Discovers the locales an application is actually translated into by scanning the
 * classpath for {@code <basename>_<locale>.properties} resource bundles.
 *
 * <p>Unlike {@link java.util.Locale#getAvailableLocales()} (which returns every locale
 * the JVM knows about), this returns only the locales that have a matching message
 * bundle plus the configured default locale, making it suitable for driving a language
 * selector. The result is sorted by each locale's display name in its own language and
 * cached; {@link #clearCache()} forces a re-scan (used when bundles change in development).
 *
 * <p>By default only the application's own {@code messages_*.properties} bundles are
 * scanned. When {@code includePluginBundles} is enabled, every {@code *.properties}
 * bundle on the classpath is considered, so locales contributed by plugins &mdash; whose
 * bundles are namespaced (e.g. {@code spring-security-core_fr.properties}) &mdash; are
 * included too. Candidate suffixes are validated against the ISO language codes (and the ISO
 * country codes when a country is present), so non-i18n properties files (e.g.
 * {@code application.properties} or {@code foo_en_prod.properties}) are ignored. See
 * {@code grails.i18n.availableLocales.includePlugins}.
 *
 * @since 8.0.0
 */
public class AvailableLocaleResolver {

    private static final Logger log = LoggerFactory.getLogger(AvailableLocaleResolver.class);

    /** The base name of an application's own message bundles ({@code messages.properties}). */
    public static final String DEFAULT_BASE_NAME = "messages";

    private static final String PROPERTIES_SUFFIX = ".properties";

    private static final Set<String> ISO_LANGUAGES = new HashSet<>(Arrays.asList(Locale.getISOLanguages()));

    private static final Set<String> ISO_COUNTRIES = new HashSet<>(Arrays.asList(Locale.getISOCountries()));

    private final ResourcePatternResolver resourcePatternResolver;

    private final Locale defaultLocale;

    private final boolean includePluginBundles;

    private volatile List<Locale> cachedLocales;

    /**
     * Scans only the application's own {@code messages_*.properties} bundles.
     *
     * @param classLoader the class loader whose classpath is scanned for message bundles
     * @param defaultLocale the locale of the base {@code messages.properties} bundle,
     * always included in the result (may be {@code null} to include none)
     */
    public AvailableLocaleResolver(ClassLoader classLoader, Locale defaultLocale) {
        this(classLoader, defaultLocale, false);
    }

    /**
     * @param classLoader the class loader whose classpath is scanned for message bundles
     * @param defaultLocale the locale of the base bundle, always included (may be {@code null})
     * @param includePluginBundles whether to also consider plugin-contributed bundles (every
     * {@code *.properties} on the classpath) rather than only the application's {@code messages}
     */
    public AvailableLocaleResolver(ClassLoader classLoader, Locale defaultLocale, boolean includePluginBundles) {
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver(classLoader);
        this.defaultLocale = defaultLocale;
        this.includePluginBundles = includePluginBundles;
    }

    /**
     * @return an unmodifiable, display-name-sorted list of the locales the application is
     * translated into. Computed once and cached until {@link #clearCache()} is called.
     */
    public List<Locale> getAvailableLocales() {
        List<Locale> locales = this.cachedLocales;
        if (locales == null) {
            synchronized (this) {
                locales = this.cachedLocales;
                if (locales == null) {
                    locales = computeAvailableLocales();
                    this.cachedLocales = locales;
                }
            }
        }
        return locales;
    }

    /**
     * Discards the cached list so the next {@link #getAvailableLocales()} re-scans the classpath.
     */
    public void clearCache() {
        this.cachedLocales = null;
    }

    private List<Locale> computeAvailableLocales() {
        Set<Locale> locales = new LinkedHashSet<>();
        if (this.defaultLocale != null && !this.defaultLocale.getLanguage().isEmpty()) {
            locales.add(this.defaultLocale);
        }
        String pattern = "classpath*:" + DEFAULT_BASE_NAME + "_*" + PROPERTIES_SUFFIX;
        if (this.includePluginBundles) {
            pattern = "classpath*:*" + PROPERTIES_SUFFIX;
        }
        try {
            for (Resource resource : this.resourcePatternResolver.getResources(pattern)) {
                Locale locale = localeFromBundleFilename(resource.getFilename());
                if (locale != null) {
                    locales.add(locale);
                }
            }
        }
        catch (IOException ex) {
            log.warn("Unable to resolve available locales from message bundles: {}", ex.getMessage());
        }
        List<Locale> sorted = new ArrayList<>(locales);
        // Sort by each locale's autonym (its name in its own language) using a fixed ROOT
        // collator: unlike natural String order this is case-insensitive and keeps accented
        // Latin letters with their base letter (e.g. "čeština" near "c"), and unlike a
        // current-locale collator the order is identical in every UI language, so the selector
        // it drives stays spatially stable for a user who arrives in a language they cannot read.
        Collator collator = Collator.getInstance(Locale.ROOT);
        sorted.sort(Comparator.comparing((Locale locale) -> locale.getDisplayName(locale), collator));
        return Collections.unmodifiableList(sorted);
    }

    /**
     * Extracts the locale a bundle filename encodes, or {@code null} if it is not a locale-specific
     * message bundle. The base name ends at the first underscore (matching Grails' own message-source
     * convention, where plugin base names use hyphens). The suffix follows the resource-bundle
     * convention {@code language(_COUNTRY(_variant))} &mdash; not BCP 47 &mdash; so the language must
     * be an ISO language and, when present, the country an ISO country; anything else (e.g.
     * {@code foo_en_prod.properties}) is rejected rather than misparsed as a script or region.
     */
    private static Locale localeFromBundleFilename(String filename) {
        if (filename == null || !filename.endsWith(PROPERTIES_SUFFIX)) {
            return null;
        }
        String base = filename.substring(0, filename.length() - PROPERTIES_SUFFIX.length());
        int underscore = base.indexOf('_');
        if (underscore < 0) {
            return null;
        }
        String[] parts = base.substring(underscore + 1).split("_", 3);
        String language = parts[0];
        if (!ISO_LANGUAGES.contains(language)) {
            return null;
        }
        if (parts.length == 1) {
            return Locale.of(language);
        }
        String country = parts[1];
        if (!ISO_COUNTRIES.contains(country)) {
            return null;
        }
        return (parts.length == 2) ? Locale.of(language, country) : Locale.of(language, country, parts[2]);
    }

}
