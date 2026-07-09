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

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.plugins.GrailsPluginManager;
import grails.util.Environment;
import org.grails.spring.context.support.PluginAwareResourceBundleMessageSource;
import org.grails.web.i18n.ParamsAwareLocaleChangeInterceptor;

@AutoConfiguration(before = { MessageSourceAutoConfiguration.class, WebMvcAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class I18nAutoConfiguration {

    @Value("${" + Settings.GSP_VIEW_ENCODING + ":UTF-8}")
    private String encoding;

    @Value("${" + Settings.GSP_ENABLE_RELOAD + ":false}")
    private boolean gspEnableReload;

    @Value("${" + Settings.I18N_CACHE_SECONDS + ":5}")
    private int cacheSeconds;

    @Value("${" + Settings.I18N_FILE_CACHE_SECONDS + ":5}")
    private int fileCacheSeconds;

    @Value("${" + Settings.I18N_LOCALE_RESOLVER + ":session}")
    private String localeResolverType;

    // Default locale for the read-only 'fixed' resolver; empty falls back to the JVM default.
    @Value("${grails.i18n.default.locale:}")
    private String defaultLocale;

    enum LocaleResolverStrategy { SESSION, COOKIE, ACCEPT_HEADER, FIXED }

    // Normalizes the configured strategy, ignoring case and separators (e.g. 'accept-header').
    static LocaleResolverStrategy resolveStrategy(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (normalized) {
            case "cookie" -> LocaleResolverStrategy.COOKIE;
            case "acceptheader", "header", "accept" -> LocaleResolverStrategy.ACCEPT_HEADER;
            case "fixed" -> LocaleResolverStrategy.FIXED;
            default -> LocaleResolverStrategy.SESSION;
        };
    }

    // SearchStrategy.CURRENT on all three guards: DispatcherServlet resolves these beans from its
    // own context, and Boot's MessageSourceAutoConfiguration uses the same scoping — a bean in a
    // parent context must not make the child context's bean back off.
    @Bean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    @ConditionalOnMissingBean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME, search = SearchStrategy.CURRENT)
    public LocaleResolver localeResolver() {
        return switch (resolveStrategy(localeResolverType)) {
            case COOKIE -> new CookieLocaleResolver("locale");
            case ACCEPT_HEADER -> new AcceptHeaderLocaleResolver();
            case FIXED -> new FixedLocaleResolver(fixedLocale());
            case SESSION -> new SessionLocaleResolver();
        };
    }

    private Locale fixedLocale() {
        if (StringUtils.hasText(defaultLocale)) {
            Locale parsed = StringUtils.parseLocale(defaultLocale);
            if (parsed != null) {
                return parsed;
            }
        }
        return Locale.getDefault();
    }

    // The ?lang= interceptor is always registered. When the configured LocaleResolver is read-only
    // (ACCEPT_HEADER or FIXED), ParamsAwareLocaleChangeInterceptor detects that the resolver cannot
    // change and ignores the parameter, so ?lang= simply has no effect.
    @Bean
    @ConditionalOnMissingBean(name = "localeChangeInterceptor", search = SearchStrategy.CURRENT)
    public LocaleChangeInterceptor localeChangeInterceptor() {
        ParamsAwareLocaleChangeInterceptor localeChangeInterceptor = new ParamsAwareLocaleChangeInterceptor();
        localeChangeInterceptor.setParamName("lang");
        return localeChangeInterceptor;
    }

    @Bean(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
    @ConditionalOnMissingBean(name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME, search = SearchStrategy.CURRENT)
    public MessageSource messageSource(GrailsApplication grailsApplication, GrailsPluginManager pluginManager) {
        PluginAwareResourceBundleMessageSource messageSource = new PluginAwareResourceBundleMessageSource(grailsApplication, pluginManager);
        messageSource.setDefaultEncoding(encoding);
        messageSource.setFallbackToSystemLocale(false);
        if (Environment.getCurrent().isReloadEnabled() || gspEnableReload) {
            messageSource.setCacheSeconds(cacheSeconds);
            messageSource.setFileCacheSeconds(fileCacheSeconds);
        }
        return messageSource;
    }
}
