/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.plugins.i18n;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The locales an application is actually translated into, for driving a language selector.
 *
 * <p>Unlike {@link java.util.Locale#getAvailableLocales()}, which lists every locale the JVM knows,
 * this lists only locales that have a message bundle, plus the configured default locale.</p>
 *
 * <p>The locales come from the same {@link EffectiveI18nDescriptors} that produce
 * {@code spring.messages.basename}, so a plugin excluded from message resolution — evicted, filtered
 * out by environment, or failed to load — cannot advertise its language here either. Offering a
 * translation whose messages do not resolve would be worse than not offering it.</p>
 *
 * @since 8.0.0
 */
public class AvailableLocaleResolver {

    /** The base name of an application's own message bundles ({@code messages.properties}). */
    public static final String DEFAULT_BASE_NAME = "messages";

    private final Supplier<EffectiveI18nDescriptors> descriptors;

    private final Locale defaultLocale;

    private volatile List<Locale> cachedLocales;

    /**
     * @param descriptors supplies the effective descriptors; re-invoked after {@link #clearCache()}
     * so that a descriptor regenerated during development is picked up
     * @param defaultLocale the locale of the base bundle, always included (may be {@code null})
     */
    public AvailableLocaleResolver(Supplier<EffectiveI18nDescriptors> descriptors, Locale defaultLocale) {
        this.descriptors = descriptors;
        this.defaultLocale = defaultLocale;
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
     * Discards the cached list so the next {@link #getAvailableLocales()} re-reads the descriptors.
     *
     * <p>During development the Grails Gradle plugin regenerates the descriptor when a bundle is
     * added or removed, so a re-read picks up a newly translated language without a restart.</p>
     */
    public void clearCache() {
        this.cachedLocales = null;
    }

    private List<Locale> computeAvailableLocales() {
        Set<Locale> locales = new LinkedHashSet<>();
        if (this.defaultLocale != null && !this.defaultLocale.getLanguage().isEmpty()) {
            locales.add(this.defaultLocale);
        }
        for (String identifier : this.descriptors.get().locales()) {
            Locale locale = parseLocale(identifier);
            if (locale != null) {
                locales.add(locale);
            }
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
     * Builds a {@link Locale} from a descriptor identifier such as {@code de}, {@code pt_BR} or
     * {@code de_AT_oo}. The identifiers follow the {@link java.util.ResourceBundle} convention
     * {@code language(_COUNTRY(_variant))} and were already validated against the ISO codes when the
     * descriptor was generated, so no validation is repeated here.
     */
    private static Locale parseLocale(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        String[] parts = identifier.split("_", 3);
        return switch (parts.length) {
            case 1 -> Locale.of(parts[0]);
            case 2 -> Locale.of(parts[0], parts[1]);
            default -> Locale.of(parts[0], parts[1], parts[2]);
        };
    }
}
