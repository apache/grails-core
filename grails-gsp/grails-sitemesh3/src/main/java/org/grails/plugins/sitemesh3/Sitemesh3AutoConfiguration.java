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
package org.grails.plugins.sitemesh3;

import org.sitemesh.webmvc.SiteMeshViewResolverBeanPostProcessor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

import grails.config.Config;
import grails.core.GrailsApplication;
import grails.util.Environment;
import grails.util.Metadata;

import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator;

/**
 * Registers the Grails SiteMesh 3 integration beans ahead of the upstream
 * auto-configuration: the {@link GrailsSiteMeshViewResolverBeanPostProcessor},
 * the {@link CaptureAwareContentProcessor} ({@code contentProcessor}) and the
 * {@link Sitemesh3LayoutFinder} ({@code decoratorSelector}).
 *
 * <p>Upstream's {@code SiteMeshViewResolverAutoConfiguration} declares each of
 * these with a {@code @ConditionalOnMissingBean} guard. By scheduling this
 * configuration first (via {@link AutoConfigureBefore}) the Grails
 * implementations are registered before those guards are evaluated, so the
 * upstream defaults back off cleanly rather than being registered and then
 * overridden after the fact.</p>
 *
 * <p>The {@code contentProcessor} and {@code decoratorSelector} beans drive view
 * decoration, which is only meaningful when Spring MVC is resolving views, so
 * they are gated on a {@link DispatcherServlet} being present. This keeps them
 * out of the lightweight unit-test contexts built by grails-testing-support,
 * which have no dispatcher servlet.</p>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration")
@AutoConfigureBefore(name = "org.sitemesh.autoconfigure.SiteMeshViewResolverAutoConfiguration")
@ConditionalOnClass(SiteMeshViewResolverBeanPostProcessor.class)
@ConditionalOnProperty(name = "sitemesh.integration", havingValue = "view-resolver", matchIfMissing = true)
public class Sitemesh3AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SiteMeshViewResolverBeanPostProcessor.class)
    public static GrailsSiteMeshViewResolverBeanPostProcessor siteMeshViewResolverBeanPostProcessor() {
        return new GrailsSiteMeshViewResolverBeanPostProcessor();
    }

    @Bean
    @ConditionalOnBean(DispatcherServlet.class)
    @ConditionalOnMissingBean(name = "contentProcessor")
    public CaptureAwareContentProcessor contentProcessor() {
        return new CaptureAwareContentProcessor();
    }

    @Bean
    @ConditionalOnBean(DispatcherServlet.class)
    @ConditionalOnMissingBean(name = "decoratorSelector")
    public Sitemesh3LayoutFinder decoratorSelector(ObjectProvider<GrailsConventionGroovyPageLocator> groovyPageLocator,
                                                   GrailsApplication grailsApplication) {
        Config config = grailsApplication.getConfig();
        Environment env = Environment.getCurrent();
        boolean developmentMode = Metadata.getCurrent().isDevelopmentEnvironmentAvailable();
        boolean reloadEnabled = env.isReloadEnabled()
                || config.getProperty("grails.gsp.enable.reload", Boolean.class, false)
                || (developmentMode && env == Environment.DEVELOPMENT);

        // The SiteMesh 3 specific key wins; fall back to the SiteMesh 2 plugin's
        // grails.views.layout.default so existing apps keep their configured
        // default layout when switching.
        String defaultLayout = config.getProperty("grails.sitemesh.default.layout");
        if (defaultLayout == null || defaultLayout.isEmpty()) {
            defaultLayout = config.getProperty("grails.views.layout.default");
        }

        Sitemesh3LayoutFinder finder = new Sitemesh3LayoutFinder(groovyPageLocator.getIfAvailable());
        finder.setGspReloadEnabled(reloadEnabled);
        finder.setDefaultDecoratorName(defaultLayout == null || defaultLayout.isEmpty() ? null : defaultLayout);
        finder.setLayoutCacheExpirationMillis(config.getProperty("grails.sitemesh.layout.cache.interval", Long.class, 5000L));
        return finder;
    }
}
