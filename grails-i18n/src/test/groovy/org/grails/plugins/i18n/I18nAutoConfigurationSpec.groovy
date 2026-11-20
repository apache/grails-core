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

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.MessageSource
import org.springframework.context.support.GenericApplicationContext
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.context.support.StaticMessageSource
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import org.springframework.web.servlet.i18n.CookieLocaleResolver
import org.springframework.web.servlet.i18n.FixedLocaleResolver
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor
import org.springframework.web.servlet.i18n.SessionLocaleResolver

import org.grails.web.i18n.ParamsAwareLocaleChangeInterceptor

import spock.lang.Specification

class I18nAutoConfigurationSpec extends Specification {

    private static AvailableLocaleResolver testResolver(Locale defaultLocale = Locale.forLanguageTag('en')) {
        ClassLoader classLoader = AvailableLocaleResolver.classLoader
        Supplier<EffectiveI18nDescriptors> descriptors = {
            EffectiveI18nDescriptors.of(I18nDescriptors.load(classLoader), [], true)
        } as Supplier<EffectiveI18nDescriptors>
        new AvailableLocaleResolver(descriptors, defaultLocale)
    }


    private WebApplicationContextRunner contextRunner() {
        GrailsApplication grailsApplication = new DefaultGrailsApplication()
        GrailsPluginManager pluginManager = Mock(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }
        Supplier<GrailsApplication> grailsApplicationSupplier = () -> grailsApplication
        Supplier<GrailsPluginManager> pluginManagerSupplier = () -> pluginManager
        new WebApplicationContextRunner()
                .withBean(GrailsApplication, grailsApplicationSupplier)
                .withBean(GrailsPluginManager, pluginManagerSupplier)
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration, I18nAutoConfiguration))
    }

    void 'the Grails i18n beans register by default'() {
        expect:
        contextRunner().run { context ->
            assert context.getBean('localeResolver') instanceof SessionLocaleResolver
            assert context.getBean(LocaleChangeInterceptor) instanceof ParamsAwareLocaleChangeInterceptor
            assert context.getBean(AvailableLocaleResolver) != null
        }
    }

    void 'Spring Boot owns the messageSource bean'() {
        expect: "Grails contributes no messageSource of its own, so Boot's auto-configuration wins its " +
                '@ConditionalOnMissingBean and supplies a stock ResourceBundleMessageSource'
        contextRunner()
                .withConfiguration(AutoConfigurations.of(MessageSourceAutoConfiguration))
                .run { context ->
                    assert context.getBean('messageSource') instanceof ResourceBundleMessageSource
                }
    }

    void 'the Grails i18n auto-configuration contributes no message source on its own'() {
        expect: 'without Boot\'s auto-configuration only the container\'s empty fallback remains'
        contextRunner().run { context ->
            assert !(context.getBean('messageSource') instanceof ResourceBundleMessageSource)
        }
    }

    void 'the Grails i18n beans do not register outside a servlet web application context'() {
        given: "the same setup as contextRunner(), but a plain (non-web) ApplicationContextRunner"
        GrailsApplication grailsApplication = new DefaultGrailsApplication()
        GrailsPluginManager pluginManager = Mock(GrailsPluginManager) {
            getAllPlugins() >> ([] as GrailsPlugin[])
        }

        expect: "@ConditionalOnWebApplication(SERVLET) on the generated I18nAutoConfiguration - the class Spring " +
                "Boot actually evaluates, not I18nGrailsPlugin itself - backs the whole auto-configuration off, " +
                "matching the hand-written I18nAutoConfiguration.java's behaviour before it moved into " +
                "I18nGrailsPlugin.groovy's @GrailsBeans block"
        new ApplicationContextRunner()
                .withBean(GrailsApplication, () -> grailsApplication)
                .withBean(GrailsPluginManager, () -> pluginManager)
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration, I18nAutoConfiguration))
                .run { context ->
                    assert !context.containsBean('localeResolver')
                    assert !context.containsBean('localeChangeInterceptor')
                    assert !context.containsBean('availableLocaleResolver')
                    // every ApplicationContext registers a fallback DelegatingMessageSource under this
                    // name if nothing else defines one, so check the bean's type rather than presence
                    assert !(context.getBean('messageSource') instanceof ResourceBundleMessageSource)
                }
    }

    void 'grails.i18n.localeResolver=cookie uses a CookieLocaleResolver and keeps the ?lang= interceptor'() {
        expect:
        contextRunner()
                .withPropertyValues("${Settings.I18N_LOCALE_RESOLVER}=cookie")
                .run { context ->
                    assert context.getBean('localeResolver') instanceof CookieLocaleResolver
                    assert context.getBean(LocaleChangeInterceptor) instanceof ParamsAwareLocaleChangeInterceptor
                }
    }

    void 'grails.i18n.localeResolver=acceptHeader uses an AcceptHeaderLocaleResolver'() {
        expect:
        contextRunner()
                .withPropertyValues("${Settings.I18N_LOCALE_RESOLVER}=acceptHeader")
                .run { context ->
                    assert context.getBean('localeResolver') instanceof AcceptHeaderLocaleResolver
                    // the ?lang= interceptor is still registered; it no-ops for a read-only resolver
                    assert context.getBean(LocaleChangeInterceptor) instanceof ParamsAwareLocaleChangeInterceptor
                }
    }

    void 'grails.i18n.localeResolver=fixed uses a FixedLocaleResolver honouring grails.i18n.default.locale'() {
        expect:
        contextRunner()
                .withPropertyValues(
                        "${Settings.I18N_LOCALE_RESOLVER}=fixed",
                        'grails.i18n.default.locale=de')
                .run { context ->
                    def resolver = context.getBean('localeResolver')
                    assert resolver instanceof FixedLocaleResolver
                    assert resolver.resolveLocale(null).language == 'de'
                    assert context.getBean(LocaleChangeInterceptor) instanceof ParamsAwareLocaleChangeInterceptor
                }
    }

    void 'a user-defined localeResolver bean makes the Grails localeResolver back off'() {
        given:
        LocaleResolver userLocaleResolver = new FixedLocaleResolver(Locale.CANADA)
        Supplier<LocaleResolver> userLocaleResolverSupplier = () -> userLocaleResolver

        expect:
        contextRunner()
                .withBean('localeResolver', LocaleResolver, userLocaleResolverSupplier)
                .run { context ->
                    assert context.getBean('localeResolver').is(userLocaleResolver)
                    assert context.getBeanNamesForType(LocaleResolver).length == 1
                }
    }

    void 'a user-defined localeChangeInterceptor bean makes the Grails localeChangeInterceptor back off'() {
        given:
        LocaleChangeInterceptor userInterceptor = new LocaleChangeInterceptor()
        Supplier<LocaleChangeInterceptor> userInterceptorSupplier = () -> userInterceptor

        expect:
        contextRunner()
                .withBean('localeChangeInterceptor', LocaleChangeInterceptor, userInterceptorSupplier)
                .run { context ->
                    assert context.getBean('localeChangeInterceptor').is(userInterceptor)
                    assert context.getBeanNamesForType(LocaleChangeInterceptor).length == 1
                }
    }

    void 'beans in a parent context do not suppress the Grails i18n beans'() {
        given: 'a parent context that already has i18n beans (SearchStrategy.CURRENT contract)'
        GenericApplicationContext parent = new GenericApplicationContext()
        parent.beanFactory.registerSingleton('localeResolver', new FixedLocaleResolver(Locale.CANADA))
        parent.beanFactory.registerSingleton('localeChangeInterceptor', new LocaleChangeInterceptor())
        parent.refresh()

        expect: 'the child context still registers its own Grails i18n beans'
        contextRunner()
                .withParent(parent)
                .run { context ->
                    assert context.getBean('localeResolver') instanceof SessionLocaleResolver
                    assert context.getBean(LocaleChangeInterceptor) instanceof ParamsAwareLocaleChangeInterceptor
                }

        cleanup:
        parent.close()
    }

    void 'the availableLocaleResolver bean registers by default from the descriptors'() {
        expect:
        contextRunner().run { context ->
            // bean(AvailableLocaleResolver) has no explicit name in the DSL - pins down that
            // Introspector.decapitalize really does derive 'availableLocaleResolver', not just that
            // some bean of the right type exists under an unrelated name
            assert context.containsBean('availableLocaleResolver')

            def resolver = context.getBean(AvailableLocaleResolver)
            // without grails.i18n.default.locale the JVM default is included (same fallback the
            // fixed localeResolver uses), alongside every locale the application descriptor records
            assert resolver.availableLocales.contains(Locale.getDefault())
            assert resolver.availableLocales.contains(Locale.forLanguageTag('fr'))
        }
    }

    void 'grails.i18n.default.locale and include-plugin-bundles drive the availableLocaleResolver'() {
        expect:
        contextRunner()
                .withPropertyValues(
                        'grails.i18n.default.locale=pt_BR',
                        'grails.i18n.include-plugin-bundles=false')
                .run { context ->
                    def resolver = context.getBean(AvailableLocaleResolver)
                    // the underscore form is normalised to a language tag
                    assert resolver.availableLocales.contains(Locale.forLanguageTag('pt-BR'))
                    // the application's own locales still participate; only plugin bundles are excluded
                    assert resolver.availableLocales.contains(Locale.forLanguageTag('fr'))
                }
    }

    void 'a user-defined AvailableLocaleResolver bean makes the Grails availableLocaleResolver back off'() {
        given:
        AvailableLocaleResolver userResolver = testResolver()
        Supplier<AvailableLocaleResolver> userResolverSupplier = () -> userResolver

        expect:
        contextRunner()
                .withBean('availableLocaleResolver', AvailableLocaleResolver, userResolverSupplier)
                .run { context ->
                    assert context.getBean(AvailableLocaleResolver).is(userResolver)
                    assert context.getBeanNamesForType(AvailableLocaleResolver).length == 1
                }
    }

}
