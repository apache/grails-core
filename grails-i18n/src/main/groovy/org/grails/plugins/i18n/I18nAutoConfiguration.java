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

import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.plugins.GrailsPluginManager;
import grails.util.Environment;
import org.grails.spring.context.support.PluginAwareResourceBundleMessageSource;
import org.grails.web.i18n.ParamsAwareLocaleChangeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@AutoConfiguration(before = {MessageSourceAutoConfiguration.class, WebMvcAutoConfiguration.class})
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

    @Bean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    public LocaleResolver localeResolver() {
        return new SessionLocaleResolver();
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        ParamsAwareLocaleChangeInterceptor localeChangeInterceptor = new ParamsAwareLocaleChangeInterceptor();
        localeChangeInterceptor.setParamName("lang");
        return localeChangeInterceptor;
    }

    @Bean(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
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
