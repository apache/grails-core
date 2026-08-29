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
package org.grails.plugins.sitemesh3

import groovy.transform.CompileStatic

import org.sitemesh.webmvc.SiteMeshViewResolverBeanPostProcessor
import org.sitemesh.webmvc.SiteMeshViewResolverPostProcessor

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.servlet.DispatcherServlet

import grails.config.Config
import grails.core.GrailsApplication
import grails.plugins.Plugin
import grails.util.Environment as GrailsEnvironment
import grails.util.Metadata
import org.grails.plugins.web.taglib.RenderSitemeshTagLib
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator

/**
 * Provides GSP layout decoration through SiteMesh 3's filter-less,
 * view-resolver-based integration. It is a drop-in replacement for the
 * grails-layout module; the two are mutually exclusive. Because this module
 * arrives transitively through {@code grails-dependencies-starter-web}, an
 * application that declares grails-layout can end up with both on the
 * classpath — that state is tolerated for migration compatibility (SiteMesh 2
 * keeps decorating and this module stands down) but warned about by
 * {@link Sitemesh3EnvironmentPostProcessor}, and support for it may be removed.
 *
 * <p>Default configuration properties are contributed by
 * {@link Sitemesh3EnvironmentPostProcessor} (registered in
 * {@code META-INF/spring.factories}). The view-resolver decoration beans are
 * declared in this class's {@code beans} block, which {@code @GrailsBeans}
 * compiles into the generated {@code Sitemesh3AutoConfiguration} class — the
 * Spring annotations above this class gate and order that auto-configuration,
 * not the plugin itself, and move onto it at compile time.</p>
 *
 * <p>The decoration beans register ahead of the upstream auto-configuration:
 * the {@link Sitemesh3ViewResolverDefinitionPostProcessor} (which rewrites the
 * {@code jspViewResolver} definition into the decorating
 * {@link GrailsSiteMeshViewResolver}), the
 * {@link GrailsSiteMeshViewResolverBeanPostProcessor}, the
 * {@link CaptureAwareContentProcessor} ({@code contentProcessor}) and the
 * {@link Sitemesh3LayoutFinder} ({@code decoratorSelector}).</p>
 *
 * <p>The definition-level rewrite is what applies decoration: because it acts
 * on the bean definition, the decorating resolver is what gets instantiated no
 * matter how early a consumer forces the lazy {@code jspViewResolver} into
 * existence (see {@link Sitemesh3ViewResolverDefinitionPostProcessor}, the
 * Grails implementation of upstream's {@code bean-definition} wrap mode). The
 * bean post-processor is the fallback tier: it decorates a
 * {@code jspViewResolver} registered as an instance rather than a definition.
 * Upstream's post-processor never re-wraps a resolver that is already a
 * {@code SiteMeshViewResolver}, so the two tiers cannot double-decorate.</p>
 *
 * <p>Upstream's {@code SiteMeshViewResolverAutoConfiguration} declares its
 * beans with {@code @ConditionalOnMissingBean} guards. By scheduling the
 * generated configuration first (via {@link AutoConfigureBefore}) the Grails
 * implementations are registered before those guards are evaluated, so the
 * upstream defaults back off cleanly rather than being registered and then
 * overridden after the fact. The two post-processors here cover all of
 * upstream's registrations by type: the definition post-processor preempts the
 * {@code bean-definition} mode bean, and the bean post-processor preempts both
 * the wrap-all and {@code bean-instance} mode beans.</p>
 *
 * <p>The {@code contentProcessor} and {@code decoratorSelector} beans drive view
 * decoration, which is only meaningful when Spring MVC is resolving views, so
 * they are gated on a {@link DispatcherServlet} being present. This keeps them
 * out of the lightweight unit-test contexts built by grails-testing-support,
 * which have no dispatcher servlet — and because the definition post-processor
 * only rewrites {@code jspViewResolver} when both of those beans are registered,
 * it keeps decoration out of such contexts too.</p>
 */
@CompileStatic
@AutoConfiguration
@AutoConfigureAfter(name = 'org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration')
@AutoConfigureBefore(name = 'org.sitemesh.autoconfigure.SiteMeshViewResolverAutoConfiguration')
@ConditionalOnClass(SiteMeshViewResolverBeanPostProcessor)
@ConditionalOnProperty(name = 'sitemesh.integration', havingValue = 'view-resolver', matchIfMissing = true)
class Sitemesh3GrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0-SNAPSHOT > *'

    def title = 'SiteMesh 3'
    def author = 'Scott Murphy'
    def authorEmail = ''
    def description = 'Provides GSP layout decoration using SiteMesh 3'
    def profiles = ['web']

    def license = 'APACHE'

    def developers = [[name: 'Scott Murphy']]

    def loadBefore = ['groovyPages']

    def providedArtefacts = [
            RenderSitemeshTagLib,
            Sitemesh3LayoutTagLib,
    ]

    def beans = {
        // The SiteMesh 2 module (grails-layout) owns view-resolver decoration when it shares the
        // classpath, so this stands down. @ConditionalOnMissingClass names the same marker class
        // Sitemesh3EnvironmentPostProcessor.SITEMESH2_MARKER_CLASS holds - an .annotate(...)
        // attribute must be an inline constant.
        bean('grailsRenderViewMutator', Sitemesh3RenderViewMutator)
                .annotate(ConditionalOnMissingClass, value: 'org.apache.grails.web.layout.GrailsLayoutViewResolverPostProcessor')

        bean('siteMeshViewResolverPostProcessor', Sitemesh3ViewResolverDefinitionPostProcessor).staticMethod().conditionalOnMissingBean(SiteMeshViewResolverPostProcessor) {
            new Sitemesh3ViewResolverDefinitionPostProcessor()
        }

        bean('siteMeshViewResolverBeanPostProcessor', GrailsSiteMeshViewResolverBeanPostProcessor).staticMethod().conditionalOnMissingBean(SiteMeshViewResolverBeanPostProcessor)

        bean('contentProcessor', CaptureAwareContentProcessor).annotate(ConditionalOnBean, value: DispatcherServlet).conditionalOnMissingBeanName()

        bean('decoratorSelector', Sitemesh3LayoutFinder).annotate(ConditionalOnBean, value: DispatcherServlet).conditionalOnMissingBeanName() { ObjectProvider<GrailsConventionGroovyPageLocator> groovyPageLocator,
                GrailsApplication grailsApplication ->
            Config config = grailsApplication.config
            GrailsEnvironment env = GrailsEnvironment.current
            boolean developmentMode = Metadata.current.isDevelopmentEnvironmentAvailable()
            boolean reloadEnabled = env.reloadEnabled ||
                    config.getProperty('grails.gsp.enable.reload', Boolean, false) ||
                    (developmentMode && env == GrailsEnvironment.DEVELOPMENT)

            // The SiteMesh 3 specific key wins; fall back to the legacy
            // grails.views.layout.default key so existing apps keep their
            // configured default layout when switching.
            String defaultLayout = config.getProperty('grails.sitemesh.default.layout') ?:
                    config.getProperty('grails.views.layout.default')

            new Sitemesh3LayoutFinder(groovyPageLocator.ifAvailable).tap {
                gspReloadEnabled = reloadEnabled
                defaultDecoratorName = defaultLayout ?: null
                layoutCacheExpirationMillis = config.getProperty('grails.sitemesh.layout.cache.interval', Long, 5000L)
            }
        }
    }
}
