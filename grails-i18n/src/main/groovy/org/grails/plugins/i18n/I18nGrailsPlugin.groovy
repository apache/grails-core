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

import java.nio.file.Files

import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.SearchStrategy
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.core.io.FileSystemResource
import org.springframework.util.StringUtils
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import org.springframework.web.servlet.i18n.CookieLocaleResolver
import org.springframework.web.servlet.i18n.FixedLocaleResolver
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor
import org.springframework.web.servlet.i18n.SessionLocaleResolver

import grails.config.Settings
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.util.BuildSettings
import grails.util.GrailsUtil
import org.grails.web.i18n.ParamsAwareLocaleChangeInterceptor

/**
 * Configures Grails' internationalisation support.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@Slf4j
@CompileStatic
@AutoConfiguration(before = WebMvcAutoConfiguration)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class I18nGrailsPlugin extends Plugin {

    String baseDir = 'grails-app/i18n'
    String version = GrailsUtil.getGrailsVersion()
    String watchedResources = "file:./${baseDir}/**/*.properties".toString()

    /**
     * Publishes the discovered available locales to the servlet context so that views and the
     * {@code g:localeSelect available="true"} tag can render a language selector. Reading a servlet
     * context attribute keeps consumers decoupled from this module.
     */
    static final String AVAILABLE_LOCALES_ATTRIBUTE = 'availableLocales'

    def beans = {
        field('localeResolverType', String).value(Settings.I18N_LOCALE_RESOLVER, 'session')
        // Default locale for the read-only 'fixed' resolver; empty falls back to the JVM default.
        // No Settings constant exists for this key - the original file didn't use one either.
        field('defaultLocale', String).value('grails.i18n.default.locale', '')

        // Shared by localeResolver (the 'fixed' case) and availableLocaleResolver below - the only
        // helper the original file needed too, everything else lives directly in its bean method.
        method('fixedLocale', Locale) {
            if (StringUtils.hasText(defaultLocale)) {
                Locale parsed = StringUtils.parseLocale(defaultLocale)
                if (parsed != null) {
                    return parsed
                }
            }
            Locale.getDefault()
        }

        // SearchStrategy.CURRENT on all three guards below: DispatcherServlet resolves these beans
        // from its own context, and Boot's MessageSourceAutoConfiguration uses the same scoping - a
        // bean in a parent context must not make the child context's bean back off.

        // Normalizes the configured strategy, ignoring case and separators (e.g. 'accept-header').
        bean(LocaleResolver).conditionalOnMissingBeanName(search: SearchStrategy.CURRENT) {
            String normalized = localeResolverType == null ? '' :
                    localeResolverType.toLowerCase(Locale.ROOT).replaceAll('[^a-z]', '')
            switch (normalized) {
                case 'cookie':
                    return new CookieLocaleResolver('locale')
                case 'acceptheader':
                case 'header':
                case 'accept':
                    return new AcceptHeaderLocaleResolver()
                case 'fixed':
                    return new FixedLocaleResolver(fixedLocale())
                default:
                    return new SessionLocaleResolver()
            }
        }

        // The ?lang= interceptor is always registered. When the configured LocaleResolver is read-only
        // (accept-header or fixed), ParamsAwareLocaleChangeInterceptor detects that the resolver cannot
        // change and ignores the parameter, so ?lang= simply has no effect.
        //
        // The derived bean names on all three guarded beans are contractual: external code (e.g.
        // grails-test-suite-uber's PluginTests) references them by literal string, so renaming a
        // bean's type changes its derived name and breaks those references.
        bean(LocaleChangeInterceptor).conditionalOnMissingBeanName(search: SearchStrategy.CURRENT) {
            new ParamsAwareLocaleChangeInterceptor(paramName: 'lang')
        }

        // No messageSource bean: Spring Boot's MessageSourceAutoConfiguration owns it. Its base names
        // are composed by I18nEnvironmentPostProcessor from the build-time i18n descriptors, and its
        // encoding, caching and locale fallback come from spring.messages.* rather than a Grails
        // message source of our own.

        // Discovers the locales the application is translated into so a language selector can list
        // only real translations; see AvailableLocaleResolver's own class docs for the full contract.
        // The supplier is re-invoked after clearCache() so that a descriptor regenerated during
        // development is picked up without a restart.
        bean(AvailableLocaleResolver).conditionalOnMissingBean() { GrailsApplication grailsApplication,
                GrailsPluginManager pluginManager,
                @Value('${grails.i18n.include-plugin-bundles:true}') boolean includePlugins ->
            ClassLoader classLoader = grailsApplication.classLoader
            Supplier<EffectiveI18nDescriptors> descriptors = {
                // getAllPlugins() is topological order — the same order the effective base-name list
                // is derived from, so locales and messages can never disagree about which plugins
                // participate.
                EffectiveI18nDescriptors.of(I18nDescriptors.load(classLoader),
                        pluginManager.allPlugins.collect { it.name }, includePlugins)
            } as Supplier<EffectiveI18nDescriptors>
            new AvailableLocaleResolver(descriptors, fixedLocale())
        }
    }

    @Override
    void doWithApplicationContext() {
        publishAvailableLocales()
    }

    private void publishAvailableLocales() {
        def ctx = applicationContext
        if (!(ctx instanceof WebApplicationContext)) {
            return
        }
        def servletContext = ((WebApplicationContext) ctx).servletContext
        if (servletContext == null) {
            return
        }
        // resolve by type, not name: the auto-configuration backs off by type, so a user-defined
        // resolver registered under any bean name must still be published
        AvailableLocaleResolver resolver = ctx.getBeanProvider(AvailableLocaleResolver).getIfAvailable()
        if (resolver != null) {
            servletContext.setAttribute(AVAILABLE_LOCALES_ATTRIBUTE, resolver.availableLocales)
        }
    }

    @Override
    void onChange(Map<String, Object> event) {
        def ctx = applicationContext
        def application = grailsApplication
        if (!ctx) {
            log.debug(/Application context not found. Can't reload/)
            return
        }

        boolean nativeascii = application.config.getProperty('grails.enable.native2ascii', Boolean, true)
        def resourcesDir = BuildSettings.RESOURCES_DIR
        def classesDir = BuildSettings.CLASSES_DIR

        // RESOURCES_DIR is null outside a Grails build (e.g. unit tests); nothing to copy then
        if (resourcesDir?.exists() && event.source instanceof FileSystemResource) {
            // this MUST be getFile() because there's also a isFile() on this class
            File eventFile = (event.source as FileSystemResource).getFile().canonicalFile
            File i18nDir = eventFile.parentFile
            if (isChildOfFile(eventFile, i18nDir)) {
                if (i18nDir.name == 'i18n' && i18nDir.parentFile.name == 'grails-app') {
                    def appDir = i18nDir.parentFile.parentFile
                    resourcesDir = new File(appDir, BuildSettings.BUILD_RESOURCES_PATH)
                    classesDir = new File(appDir, BuildSettings.BUILD_CLASSES_PATH)
                }

                if (nativeascii) {
                    // if native2ascii is enabled then read the properties and write them out again
                    // so that unicode escaping is applied
                    def properties = new Properties()
                    eventFile.withReader {
                        properties.load(it)
                    }
                    // by using an OutputStream the unicode characters will be escaped
                    new File(resourcesDir, eventFile.name).withOutputStream {
                        properties.store(it, '')
                    }
                    new File(classesDir, eventFile.name).withOutputStream {
                        properties.store(it, '')
                    }
                } else {
                    // otherwise just copy the file as is
                    Files.copy(eventFile.toPath(), new File(resourcesDir, eventFile.name).toPath())
                    Files.copy(eventFile.toPath(), new File(classesDir, eventFile.name).toPath())
                }

            }
        }

        // Boot's ResourceBundleMessageSource reads through java.util.ResourceBundle and exposes no
        // clearCache() of its own, so the bundle cache is flushed at its source. Combined with a short
        // spring.messages.cache-duration in development, the edited bundle is picked up on the next
        // lookup. Note this only reloads *content*: a newly added base name is not in
        // spring.messages.basename, which Boot reads once when it builds the message source.
        ResourceBundle.clearCache(Thread.currentThread().contextClassLoader)

        // A bundle may have been added/removed, so re-read the descriptors and re-publish the locales.
        AvailableLocaleResolver availableLocaleResolver = ctx.getBeanProvider(AvailableLocaleResolver).getIfAvailable()
        if (availableLocaleResolver != null) {
            availableLocaleResolver.clearCache()
            publishAvailableLocales()
        }
    }

    protected boolean isChildOfFile(File child, File parent) {
        def currentFile = child.canonicalFile
        while (currentFile != null) {
            if (currentFile == parent) {
                return true
            }
            currentFile = currentFile.parentFile
        }
        return false
    }

}
