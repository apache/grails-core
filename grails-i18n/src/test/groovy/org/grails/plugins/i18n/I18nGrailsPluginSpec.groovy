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

import grails.core.DefaultGrailsApplication

import org.springframework.context.support.GenericApplicationContext
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.support.StaticWebApplicationContext

import java.util.function.Supplier

import spock.lang.Specification

/**
 * Tests the available-locale publishing lifecycle of {@link I18nGrailsPlugin}: the discovered
 * locales are published to the servlet context on startup and republished (after a re-scan)
 * when a message bundle changes.
 */
class I18nGrailsPluginSpec extends Specification {

    private static AvailableLocaleResolver testResolver(Locale defaultLocale = Locale.forLanguageTag('en')) {
        ClassLoader classLoader = AvailableLocaleResolver.classLoader
        Supplier<EffectiveI18nDescriptors> descriptors = {
            EffectiveI18nDescriptors.of(I18nDescriptors.load(classLoader), [], true)
        } as Supplier<EffectiveI18nDescriptors>
        new AvailableLocaleResolver(descriptors, defaultLocale)
    }


    private static StaticWebApplicationContext webContext(AvailableLocaleResolver resolver = null,
                                                          String beanName = 'availableLocaleResolver') {
        StaticWebApplicationContext ctx = new StaticWebApplicationContext()
        ctx.servletContext = new MockServletContext()
        if (resolver != null) {
            ctx.beanFactory.registerSingleton(beanName, resolver)
        }
        // StaticApplicationContext already registers a StaticMessageSource under 'messageSource',
        // which is what onChange() looks up
        ctx.refresh()
        ctx
    }

    private static I18nGrailsPlugin pluginFor(StaticWebApplicationContext ctx) {
        I18nGrailsPlugin plugin = new I18nGrailsPlugin()
        plugin.applicationContext = ctx
        plugin.grailsApplication = new DefaultGrailsApplication()
        plugin
    }

    void 'doWithApplicationContext publishes the available locales to the servlet context'() {
        given:
        AvailableLocaleResolver resolver = testResolver()
        StaticWebApplicationContext ctx = webContext(resolver)
        I18nGrailsPlugin plugin = pluginFor(ctx)

        when:
        plugin.doWithApplicationContext()

        then: 'views read the exact list the resolver computed'
        ctx.servletContext.getAttribute('availableLocales').is(resolver.availableLocales)

        cleanup:
        ctx.close()
    }

    void 'a user-defined resolver under a custom bean name is still published (lookup is by type)'() {
        given: 'the auto-configuration backs off by type, so the plugin must find the bean by type too'
        AvailableLocaleResolver resolver = testResolver()
        StaticWebApplicationContext ctx = webContext(resolver, 'myOwnLocaleResolver')
        I18nGrailsPlugin plugin = pluginFor(ctx)

        when:
        plugin.doWithApplicationContext()

        then:
        ctx.servletContext.getAttribute('availableLocales').is(resolver.availableLocales)

        cleanup:
        ctx.close()
    }

    void 'doWithApplicationContext is a no-op outside a web application context'() {
        given:
        GenericApplicationContext ctx = new GenericApplicationContext()
        ctx.refresh()
        I18nGrailsPlugin plugin = new I18nGrailsPlugin()
        plugin.applicationContext = ctx

        when:
        plugin.doWithApplicationContext()

        then:
        notThrown(Exception)

        cleanup:
        ctx.close()
    }

    void 'doWithApplicationContext publishes nothing when no availableLocaleResolver bean exists'() {
        given:
        StaticWebApplicationContext ctx = webContext()
        I18nGrailsPlugin plugin = pluginFor(ctx)

        when:
        plugin.doWithApplicationContext()

        then:
        ctx.servletContext.getAttribute('availableLocales') == null

        cleanup:
        ctx.close()
    }

    void 'onChange re-scans and republishes the available locales when a bundle changes'() {
        given: 'the locales have been published once and are cached'
        AvailableLocaleResolver resolver = testResolver()
        StaticWebApplicationContext ctx = webContext(resolver)
        I18nGrailsPlugin plugin = pluginFor(ctx)
        plugin.doWithApplicationContext()
        List<Locale> before = (List<Locale>) ctx.servletContext.getAttribute('availableLocales')

        when: 'a bundle-change event arrives (a non-file source, so no bundle copying is attempted)'
        plugin.onChange([source: 'messages_es.properties'] as Map<String, Object>)

        then: 'a freshly computed list with the same contents is republished'
        List<Locale> after = (List<Locale>) ctx.servletContext.getAttribute('availableLocales')
        !after.is(before)
        after == before

        cleanup:
        ctx.close()
    }
}
