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

import spock.lang.Specification

/**
 * Tests {@link AvailableLocaleResolver} against the {@code messages_*.properties} bundles on the
 * test classpath ({@code src/test/resources}: es, fr, pt_BR, plus the base {@code messages.properties}).
 */
class AvailableLocaleResolverSpec extends Specification {

    private AvailableLocaleResolver newResolver(Locale defaultLocale = Locale.forLanguageTag('en')) {
        new AvailableLocaleResolver(getClass().classLoader, defaultLocale)
    }

    void 'discovers the locales that have a messages bundle plus the default locale'() {
        when:
        List<Locale> locales = newResolver().availableLocales

        then:
        locales.contains(Locale.forLanguageTag('en'))
        locales.contains(Locale.forLanguageTag('es'))
        locales.contains(Locale.forLanguageTag('fr'))
        locales.contains(Locale.forLanguageTag('pt-BR'))
    }

    void 'plugin-namespaced bundles are discovered only when plugin bundles are included'() {
        given: 'a plugin ships spring-security-core_zu.properties (a locale no messages bundle has)'
        Locale zulu = Locale.forLanguageTag('zu')

        expect: 'a host-only scan does not see the plugin-namespaced locale'
        !new AvailableLocaleResolver(getClass().classLoader, Locale.forLanguageTag('en'), false)
                .availableLocales.contains(zulu)

        when: 'plugin bundles are included'
        List<Locale> locales = new AvailableLocaleResolver(getClass().classLoader,
                Locale.forLanguageTag('en'), true).availableLocales

        then: 'the plugin locale is discovered, alongside the host messages locales'
        locales.contains(zulu)
        locales.contains(Locale.forLanguageTag('es'))
    }

    void 'non-i18n properties files on the classpath are ignored'() {
        given:
        List<String> isoLanguages = Locale.getISOLanguages() as List

        expect: 'application.properties / logback.properties etc. never contribute a bogus locale'
        new AvailableLocaleResolver(getClass().classLoader, Locale.forLanguageTag('en'), true)
                .availableLocales.every { it.language in isoLanguages }
    }

    void 'sorts the discovered locales by their display name in their own language'() {
        given:
        List<Locale> expectedKnown = [
                Locale.forLanguageTag('en'),
                Locale.forLanguageTag('es'),
                Locale.forLanguageTag('fr'),
                Locale.forLanguageTag('pt-BR')
        ].sort { it.getDisplayName(it) }

        when: 'the discovered list is narrowed to the bundles this test controls'
        List<Locale> actualKnown = newResolver().availableLocales.findAll { it in expectedKnown }

        then: 'their relative order matches a display-name sort'
        actualKnown == expectedKnown
    }

    void 'always includes the configured default locale even without a matching bundle'() {
        expect:
        newResolver(Locale.forLanguageTag('de')).availableLocales.contains(Locale.forLanguageTag('de'))
    }

    void 'returns an unmodifiable list'() {
        when:
        newResolver().availableLocales.add(Locale.forLanguageTag('zz'))

        then:
        thrown(UnsupportedOperationException)
    }

    void 'a null default locale is simply not added'() {
        when:
        List<Locale> locales = newResolver(null).availableLocales

        then: 'the scanned bundles are still discovered'
        locales.contains(Locale.forLanguageTag('es'))
        locales.contains(Locale.forLanguageTag('fr'))

        and: 'no null or language-less entry is present'
        locales.every { it != null && it.language }
    }

    void 'a language-less default locale (Locale.ROOT) is simply not added'() {
        expect:
        !newResolver(Locale.ROOT).availableLocales.contains(Locale.ROOT)
    }

    void 'falls back to just the default locale when classpath scanning fails'() {
        given: 'a class loader whose resource enumeration always fails'
        ClassLoader failingClassLoader = new ClassLoader(null) {

            @Override
            Enumeration<URL> getResources(String name) throws IOException {
                throw new IOException('simulated classpath failure')
            }
        }

        when:
        List<Locale> locales = new AvailableLocaleResolver(failingClassLoader, Locale.forLanguageTag('en')).availableLocales

        then: 'no exception escapes and only the default locale is offered'
        locales == [Locale.forLanguageTag('en')]
    }

    void 'bundles with a malformed locale suffix never contribute a locale'() {
        given: 'deliberate fixtures: standalone.properties (no suffix), messages_.properties (empty), bogus-plugin_zz.properties (non-ISO language) and report_en_prod.properties (non-ISO country)'
        List<Locale> locales = new AvailableLocaleResolver(getClass().classLoader, null, true).availableLocales

        expect: 'the scan saw sibling fixtures (a valid plugin-namespaced bundle is discovered)'
        locales.contains(Locale.forLanguageTag('zu'))

        and: 'none of the malformed fixtures produced a locale'
        locales.every { it.language && it.language in Locale.getISOLanguages() }
        !locales.any { it.language == 'zz' }

        and: 'a non-ISO country is rejected rather than misparsed as a script or region (no en-Prod)'
        !locales.any { it.toLanguageTag().toLowerCase().contains('prod') }
    }

    void 'a language_COUNTRY suffix is parsed by resource-bundle convention'() {
        expect: 'the pt_BR fixture yields the pt-BR locale, with BR as the country'
        Locale brazilian = newResolver().availableLocales.find { it.language == 'pt' }
        brazilian.country == 'BR'
    }

    void 'caches the computed list until the cache is cleared'() {
        given:
        AvailableLocaleResolver resolver = newResolver()

        expect: 'the same instance is returned while cached'
        resolver.availableLocales.is(resolver.availableLocales)

        when: 'the cache is cleared'
        List<Locale> before = resolver.availableLocales
        resolver.clearCache()
        List<Locale> after = resolver.availableLocales

        then: 'a freshly computed list is returned with equal contents'
        !before.is(after)
        before == after
    }
}
