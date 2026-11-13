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

import org.springframework.context.support.ResourceBundleMessageSource

import spock.lang.Specification

/**
 * Guards the one behaviour that "let Boot own resolution" and "preserve existing behaviour" pull
 * against each other on.
 *
 * <p>The message source Grails used previously merged plugin bundles with {@code Map.putAll} while
 * iterating {@code GrailsPluginManager.getAllPlugins()}, so the <em>last</em> plugin in that
 * iteration won a contested code. {@code getAllPlugins()} returns plugins in <b>topological</b> order
 * — not load order; they are different lists in {@code DefaultPluginDiscovery} — and Boot's
 * {@code ResourceBundleMessageSource} takes the <em>first</em> matching base name. Reversing
 * topological order is therefore what reproduces the old precedence, and passing the topological list
 * through unreversed, or reading the load-order list instead, would silently invert it.</p>
 */
class MessageSourcePrecedenceSpec extends Specification {

    private static final String APP = 'app'

    private static I18nDescriptor application() {
        new I18nDescriptor(I18nDescriptor.TYPE_APPLICATION, APP, '1.0', ['messages'], [])
    }

    private static I18nDescriptor plugin(String name) {
        new I18nDescriptor(I18nDescriptor.TYPE_PLUGIN, name, '1.0', [name], [])
    }

    private static ResourceBundleMessageSource messageSourceFor(List<String> basenames) {
        new ResourceBundleMessageSource().tap {
            setBasenames(basenames as String[])
            defaultEncoding = 'UTF-8'
            fallbackToSystemLocale = false
        }
    }

    void 'the last plugin in topological order wins a contested code, as it did before'() {
        given: 'alpha then beta in topological order, so beta previously overwrote alpha'
        List<String> basenames = EffectiveI18nDescriptors.of(
                [application(), plugin('alpha-plugin'), plugin('beta-plugin')],
                ['alpha-plugin', 'beta-plugin'], true).basenames()

        expect: 'reversing topological order lets first-match-wins reach the same answer'
        basenames == ['messages', 'beta-plugin', 'alpha-plugin']

        and: 'both Spring resolution paths agree — they are separate methods on the message source'
        ResourceBundleMessageSource messageSource = messageSourceFor(basenames)
        messageSource.getMessage('shared.code', null, Locale.ENGLISH) == 'from beta'
        messageSource.getMessage('shared.formatted', ['hi'] as Object[], Locale.ENGLISH) == 'beta says hi'
    }

    void 'reversing the topological order reverses the winner'() {
        given: 'beta then alpha in topological order'
        List<String> basenames = EffectiveI18nDescriptors.of(
                [application(), plugin('alpha-plugin'), plugin('beta-plugin')],
                ['beta-plugin', 'alpha-plugin'], true).basenames()

        expect:
        basenames == ['messages', 'alpha-plugin', 'beta-plugin']

        and: 'so a test that read the wrong plugin list would produce the opposite result here'
        ResourceBundleMessageSource messageSource = messageSourceFor(basenames)
        messageSource.getMessage('shared.code', null, Locale.ENGLISH) == 'from alpha'
        messageSource.getMessage('shared.formatted', ['hi'] as Object[], Locale.ENGLISH) == 'alpha says hi'
    }

    void 'a code only one plugin defines resolves regardless of order'() {
        given:
        List<String> basenames = EffectiveI18nDescriptors.of(
                [application(), plugin('alpha-plugin'), plugin('beta-plugin')],
                ['alpha-plugin', 'beta-plugin'], true).basenames()
        ResourceBundleMessageSource messageSource = messageSourceFor(basenames)

        expect:
        messageSource.getMessage('alpha.only', null, Locale.ENGLISH) == 'alpha only'
        messageSource.getMessage('beta.only', null, Locale.ENGLISH) == 'beta only'
    }

    void 'the application always outranks every plugin'() {
        expect: 'the application descriptor contributes its base names ahead of any plugin'
        EffectiveI18nDescriptors.of([plugin('alpha-plugin'), application(), plugin('beta-plugin')],
                ['alpha-plugin', 'beta-plugin'], true).basenames().first() == 'messages'
    }

    void 'a plugin the application never discovered contributes nothing'() {
        expect: 'its jar is on the classpath, but it was evicted, filtered or failed to load'
        EffectiveI18nDescriptors.of([application(), plugin('alpha-plugin'), plugin('beta-plugin')],
                ['alpha-plugin'], true).basenames() == ['messages', 'alpha-plugin']
    }

    void 'include-plugin-bundles=false drops every plugin base name'() {
        expect:
        EffectiveI18nDescriptors.of([application(), plugin('alpha-plugin'), plugin('beta-plugin')],
                ['alpha-plugin', 'beta-plugin'], false).basenames() == ['messages']
    }

    void 'a base name contributed twice keeps only its first, highest-precedence position'() {
        expect:
        EffectiveI18nDescriptors.of([
                new I18nDescriptor(I18nDescriptor.TYPE_APPLICATION, APP, '1.0', ['messages'], []),
                new I18nDescriptor(I18nDescriptor.TYPE_PLUGIN, 'alpha-plugin', '1.0', ['messages'], [])],
                ['alpha-plugin'], true).basenames() == ['messages']
    }
}
