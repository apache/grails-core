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

import org.springframework.boot.SpringApplication
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.logging.DeferredLogFactory
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.util.ClassUtils

import org.grails.web.util.WebUtils

/**
 * Contributes the Grails defaults for the SiteMesh 3 configuration keys —
 * layout selection via the {@code layout} meta tag, the
 * {@code /layouts/} decorator prefix and, when configured, the application's
 * default layout — before the application context refreshes.
 *
 * <p>Because these defaults are in the {@link ConfigurableEnvironment} from the
 * start, both the SiteMesh starter's {@code @Value} placeholders and the Grails
 * configuration (which is built from the environment) observe them without any
 * post-hoc reassignment. This replaces the property-source manipulation the
 * plugin previously performed in {@code doWithSpring()}.</p>
 *
 * <p>Each default is contributed only when the application has not set the key
 * itself, and the source is appended with lowest precedence, so application
 * configuration always wins.</p>
 */
@CompileStatic
class Sitemesh3EnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * An EnvironmentPostProcessor runs before Spring Boot initialises logging
     * (its listener orders ahead of LoggingApplicationListener), so a plain
     * static logger writes into a logging system that does not exist yet and
     * the message is lost. Spring Boot passes a DeferredLogFactory to whichever
     * constructor accepts one; those records are replayed once logging is up.
     */
    private final Log log

    Sitemesh3EnvironmentPostProcessor() {
        this.log = LogFactory.getLog(Sitemesh3EnvironmentPostProcessor)
    }

    Sitemesh3EnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(Sitemesh3EnvironmentPostProcessor)
    }

    static final String PROPERTY_SOURCE_NAME = 'defaultSitemesh3Properties'

    /**
     * A class unique to the SiteMesh 2 module (grails-layout). grails-sitemesh3
     * is a drop-in replacement for it — the two modules are mutually exclusive
     * by contract. Because grails-sitemesh3 arrives transitively through
     * {@code grails-dependencies-starter-web}, an application that declares
     * grails-layout can end up with both on the classpath; that state is
     * currently tolerated for migration compatibility — the SiteMesh 2
     * integration keeps decorating and this module's view-resolver machinery
     * stands down — but it is warned about loudly and support for it may be
     * removed.
     */
    static final String SITEMESH2_MARKER_CLASS = 'org.apache.grails.web.layout.GrailsLayoutViewResolverPostProcessor'

    private static final boolean SITEMESH2_PRESENT =
            ClassUtils.isPresent(SITEMESH2_MARKER_CLASS, ClassUtils.defaultClassLoader)

    /**
     * Whether the SiteMesh 2 module (grails-layout) shares the classpath. When
     * it does, this module's view-resolver decoration stands down entirely.
     */
    static boolean isSiteMesh2Present() {
        SITEMESH2_PRESENT
    }

    @Override
    void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isSiteMesh2Present()) {
            log.error('Both grails-sitemesh3 and grails-layout (SiteMesh 2) are on the classpath. ' +
                    'They are mutually exclusive - grails-sitemesh3 is a drop-in replacement for ' +
                    'grails-layout - and an application should never depend on both. SiteMesh 2 ' +
                    'keeps decorating and SiteMesh 3 view-resolver decoration is disabled, but this ' +
                    'is a broken build rather than a supported configuration: remove the ' +
                    'grails-layout dependency, or exclude grails-sitemesh3, which arrives ' +
                    'transitively through grails-dependencies-starter-web.')
        }
        MapPropertySource defaults = getDefaultPropertySource(environment)
        if (!defaults.source.isEmpty()) {
            environment.propertySources.addLast(defaults)
        }
    }

    /**
     * The SiteMesh 3 defaults not already configured in the given environment.
     * Public because {@code grails.gsp.boot.GspAutoConfiguration} applies the
     * same defaults for plain Spring Boot GSP applications whose contexts are
     * built without {@code SpringApplication} (where no
     * {@code EnvironmentPostProcessor} runs).
     */
    static MapPropertySource getDefaultPropertySource(ConfigurableEnvironment environment) {
        Map<String, Object> properties = new LinkedHashMap<>()
        properties.put('sitemesh.decorator.metaTag', 'layout')
        properties.put('sitemesh.decorator.attribute', WebUtils.LAYOUT_ATTRIBUTE)
        properties.put('sitemesh.decorator.prefix', '/layouts/')

        // The SiteMesh 3 specific key wins; fall back to the legacy
        // grails.views.layout.default key so existing apps keep their
        // configured default layout when switching.
        String defaultLayout = environment.getProperty('grails.sitemesh.default.layout') ?:
                environment.getProperty('grails.views.layout.default')
        if (defaultLayout) {
            properties.put('sitemesh.decorator.default', defaultLayout)
        }

        properties.keySet().removeIf { String key -> environment.getProperty(key) != null }
        new MapPropertySource(PROPERTY_SOURCE_NAME, properties)
    }

    @Override
    int getOrder() {
        Ordered.LOWEST_PRECEDENCE
    }
}
