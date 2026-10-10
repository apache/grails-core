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

package org.grails.forge.template

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.io.DefaultGroovyPageLocator
import org.grails.gsp.io.GroovyPageScriptSource

/**
 * Renders the generator's pages: the GSPs of {@code grails-app/views/forge}, compiled when the module
 * declaring them is built and registered in its {@code gsp/views.properties}. They are rendered by
 * the GSP engine alone, without a web request, so the generator renders them the same way inside the
 * web application, the command line application and a test.
 */
@CompileStatic
@PackageScope
final class ForgePages {

    /**
     * The views registry every module compiling pages writes, the Forge plugin's and the command line
     * application's among them.
     */
    static final String VIEWS_REGISTRY = 'gsp/views.properties'

    /**
     * The URI prefix, in the registry, of the generator's pages.
     */
    static final String PAGES_PREFIX = '/WEB-INF/grails-app/views/forge/'

    private final GroovyPagesTemplateEngine engine
    private final Map<String, groovy.text.Template> pages = new ConcurrentHashMap<>()

    private ForgePages(ClassLoader classLoader) {
        DefaultGroovyPageLocator locator = new DefaultGroovyPageLocator() {
            @Override
            protected boolean isDevelopmentMode() {
                // the pages are only ever used compiled: their sources are not on the class path
                false
            }
        }
        locator.precompiledGspMap = compiledPages(classLoader)
        engine = new GroovyPagesTemplateEngine()
        engine.groovyPageLocator = locator
        engine.classLoader = classLoader
        engine.afterPropertiesSet()
    }

    static void render(String uri, Map<String, Object> model, Writer out) {
        Holder.INSTANCE.page(uri).make(model).writeTo(out)
    }

    private groovy.text.Template page(String uri) {
        pages.computeIfAbsent(uri) { String key ->
            GroovyPageScriptSource source = engine.findScriptSource(key)
            if (source == null) {
                throw new IllegalArgumentException("No compiled page ${key}: a page of the generator lives " +
                        'under grails-app/views/forge of a module compiling its pages')
            }
            engine.createTemplate(source)
        }
    }

    private static Map<String, String> compiledPages(ClassLoader classLoader) {
        Map<String, String> compiled = [:]
        for (URL registry : Collections.list(classLoader.getResources(VIEWS_REGISTRY))) {
            Properties views = new Properties()
            registry.withInputStream { InputStream input -> views.load(input) }
            for (String name : views.stringPropertyNames()) {
                if (name.startsWith(PAGES_PREFIX)) {
                    compiled[name] = views.getProperty(name)
                }
            }
        }
        compiled
    }

    private static final class Holder {

        static final ForgePages INSTANCE = new ForgePages(ForgePages.classLoader)

        private Holder() {
        }
    }
}
