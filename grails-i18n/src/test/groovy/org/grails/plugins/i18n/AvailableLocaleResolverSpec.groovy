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
package org.grails.plugins.i18n

import java.util.function.Supplier

import spock.lang.Specification

/**
 * Locale discovery reads the build-time descriptors rather than scanning the classpath, so file-name
 * interpretation is no longer exercised here — it happens once in the Grails Gradle plugin and is
 * covered by {@code I18nBundleIndexSpec}.
 */
class AvailableLocaleResolverSpec extends Specification {

    private static AvailableLocaleResolver resolver(List<String> locales, Locale defaultLocale) {
        Supplier<EffectiveI18nDescriptors> descriptors = {
            EffectiveI18nDescriptors.of(
                    [new I18nDescriptor(I18nDescriptor.TYPE_APPLICATION, 'app', '1.0', ['messages'], locales)],
                    [], true)
        } as Supplier<EffectiveI18nDescriptors>
        new AvailableLocaleResolver(descriptors, defaultLocale)
    }

    void 'the default locale is always offered even without a locale-specific bundle'() {
        expect:
        resolver([], Locale.forLanguageTag('en')).availableLocales == [Locale.of('en')]
    }

    void 'each descriptor locale becomes an offered locale'() {
        when:
        List<Locale> locales = resolver(['de', 'fr'], Locale.forLanguageTag('en')).availableLocales

        then:
        locales.containsAll([Locale.of('en'), Locale.of('de'), Locale.of('fr')])
        locales.size() == 3
    }

    void 'a language_COUNTRY identifier becomes a locale with that country'() {
        expect:
        resolver(['pt_BR'], null).availableLocales == [Locale.of('pt', 'BR')]
    }

    void 'locales are ordered by their own display name, keeping accented letters with their base letter'() {
        when:
        List<Locale> locales = resolver(['cs', 'de', 'da'], null).availableLocales

        then: "čeština sorts near 'c' rather than after 'z', and the order is identical in every UI language"
        locales*.toString() == ['cs', 'da', 'de']
    }

    void 'a null default locale simply contributes nothing'() {
        expect:
        resolver(['de'], null).availableLocales == [Locale.of('de')]
    }

    void 'the ROOT locale is not offered as a language'() {
        expect:
        resolver([], Locale.ROOT).availableLocales == []
    }

    void 'the result is unmodifiable'() {
        when:
        resolver(['de'], null).availableLocales << Locale.FRENCH

        then:
        thrown(UnsupportedOperationException)
    }

    void 'the locale list is cached until the cache is cleared'() {
        given: 'a supplier that reports how often the descriptors were read'
        int reads = 0
        Supplier<EffectiveI18nDescriptors> descriptors = {
            reads++
            EffectiveI18nDescriptors.of(
                    [new I18nDescriptor(I18nDescriptor.TYPE_APPLICATION, 'app', '1.0', ['messages'], ['de'])],
                    [], true)
        } as Supplier<EffectiveI18nDescriptors>
        AvailableLocaleResolver resolver = new AvailableLocaleResolver(descriptors, null)

        when:
        resolver.availableLocales
        resolver.availableLocales

        then:
        reads == 1

        when: 'a bundle was added during development and the descriptor regenerated'
        resolver.clearCache()
        resolver.availableLocales

        then: 'the descriptors are re-read rather than the stale list being reused'
        reads == 2
    }

    void 'a plugin excluded from message resolution does not advertise its locales'() {
        given: 'a plugin descriptor whose plugin the application never discovered'
        Supplier<EffectiveI18nDescriptors> descriptors = {
            EffectiveI18nDescriptors.of([
                    new I18nDescriptor(I18nDescriptor.TYPE_APPLICATION, 'app', '1.0', ['messages'], ['de']),
                    new I18nDescriptor(I18nDescriptor.TYPE_PLUGIN, 'evicted', '1.0', ['evicted'], ['ja'])],
                    [], true)
        } as Supplier<EffectiveI18nDescriptors>

        expect: 'offering Japanese would promise a translation whose messages cannot resolve'
        new AvailableLocaleResolver(descriptors, null).availableLocales == [Locale.of('de')]
    }
}
