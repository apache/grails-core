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
package org.grails.plugins.web

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.BeanRegistrar

import grails.core.gsp.GrailsTagLibClass
import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor
import grails.util.Metadata
import grails.web.pages.GroovyPagesUriService
import org.apache.grails.common.aot.AheadOfTimeProcessing
import org.apache.grails.gsp.taglib.TagLibBeanDefinitionsPostProcessor
import org.grails.core.artefact.gsp.TagLibArtefactHandler
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.plugins.web.taglib.CountryTagLib
import org.grails.plugins.web.taglib.FormTagLib
import org.grails.plugins.web.taglib.FormatTagLib
import org.grails.plugins.web.taglib.JavascriptTagLib
import org.grails.plugins.web.taglib.PluginTagLib
import org.grails.plugins.web.taglib.RenderTagLib
import org.grails.plugins.web.taglib.UrlMappingTagLib
import org.grails.plugins.web.taglib.ValidationTagLib
import org.grails.taglib.TagLibraryLookup
import org.grails.web.pages.FilteringCodecsByContentTypeSettings

/**
 * Sets up and configures the GSP and GSP tag library support in Grails.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@GrailsPluginDescriptor(
        loadAfter = ['filters'],
        dependsOn = ['core:${plugin.version}', 'i18n:${plugin.version}'],
        observe = ['controllers'],
        watchedResources = ['file:./plugins/*/grails-app/taglib/**/*TagLib.groovy',
                                'file:./grails-app/taglib/**/*TagLib.groovy'],
        providedArtefacts = [
                ApplicationTagLib,
                CountryTagLib,
                FormatTagLib,
                FormTagLib,
                JavascriptTagLib,
                RenderTagLib,
                UrlMappingTagLib,
                ValidationTagLib,
                PluginTagLib,
        ])
class GroovyPagesGrailsPlugin extends Plugin {

    public static final String GSP_RELOAD_INTERVAL = 'grails.gsp.reload.interval'
    public static final String GSP_VIEWS_DIR = 'grails.gsp.view.dir'

    /**
     * Clear the page cache with the ApplicationContext is loaded
     */
    @CompileStatic
    @Override
    void doWithApplicationContext() {
        applicationContext.getBean('groovyPagesTemplateEngine', GroovyPagesTemplateEngine).clearPageCache()
    }

    /**
     * The beans GSP needs are registered by {@link GroovyPagesBeanDefinitionsPostProcessor}, and the
     * tag library beans by {@link TagLibBeanDefinitionsPostProcessor}: tag library beans autowire by
     * name and are read from the artefacts the application knows about, and GSP's own beans include an
     * abstract parent, an alias and inner beans, none of which the
     * {@link org.springframework.beans.factory.BeanRegistry} API can express. Contributing the tag
     * libraries from a post-processor leaves the definitions an ahead-of-time image generated, and the
     * injection generated with them, in place.
     */
    @Override
    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, org.springframework.core.env.Environment environment ->
            boolean developmentMode = isDevelopmentMode()
            registry.registerBean('groovyPagesBeanDefinitionsPostProcessor', GroovyPagesBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new GroovyPagesBeanDefinitionsPostProcessor(grailsApplication, developmentMode)
                }
            }
            registry.registerBean('tagLibBeanDefinitionsPostProcessor', TagLibBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new TagLibBeanDefinitionsPostProcessor(grailsApplication)
                }
            }
        } as BeanRegistrar
    }

    /**
     * Whether the application is being developed, which decides where its pages are read from and
     * whether they are watched for change.
     *
     * <p>While code is being generated the answer is no, whatever the machine doing the generating
     * looks like. Generation runs in the project directory, so a development environment is
     * available there and the question would otherwise be answered for the machine that built the
     * application rather than the one that runs it: the pages would be read from a directory that
     * exists only on the build machine, whose path would be written into the artifact, and an image
     * cannot compile a page it finds there in any case.</p>
     */
    @CompileStatic
    protected boolean isDevelopmentMode() {
        if (AheadOfTimeProcessing.generatingCode) {
            return false
        }
        Metadata.getCurrent().isDevelopmentEnvironmentAvailable()
    }

    static String transformToValidLocation(String location) {
        if (location == '.') return location
        if (!location.endsWith(File.separator)) return "${location}${File.separator}"
        return location
    }

    @Override
    void onChange(Map<String, Object> event) {
        def application = grailsApplication
        def ctx = applicationContext

        if (application.isArtefactOfType(TagLibArtefactHandler.TYPE, event.source)) {
            GrailsTagLibClass taglibClass = (GrailsTagLibClass) application.addArtefact(TagLibArtefactHandler.TYPE, event.source)
            if (taglibClass) {
                // replace tag library bean
                def beanName = taglibClass.fullName
                beans {
                    "$beanName"(taglibClass.clazz) { bean ->
                        bean.autowire = true
                    }
                }

                // The tag library lookup class caches 'tag -> taglib class'
                // so we need to update it now.
                def lookup = applicationContext.getBean('gspTagLibraryLookup', TagLibraryLookup)
                // Registering with the lookup is enough: tags are resolved through it rather than
                // installed onto each tag library's metaclass.
                lookup.registerTagLib(taglibClass)
            }
        }
        // clear uri cache after changes
        ctx.getBean('groovyPagesUriService', GroovyPagesUriService).clear()
    }

    @CompileStatic
    void onConfigChange(Map<String, Object> event) {
        applicationContext.getBean('filteringCodecsByContentTypeSettings', FilteringCodecsByContentTypeSettings).initialize(grailsApplication)
    }

}
