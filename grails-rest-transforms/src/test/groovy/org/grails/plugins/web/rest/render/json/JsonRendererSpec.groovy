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
package org.grails.plugins.web.rest.render.json

import grails.core.DefaultGrailsApplication
import grails.persistence.Entity
import grails.rest.render.json.JsonRenderer
import grails.util.GrailsWebMockUtil
import org.grails.config.NavigableMapConfig
import org.grails.config.PropertySourcesConfig
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.springframework.context.ApplicationContext
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class JsonRendererSpec extends Specification {

    void setup() {
        final initializer = new ConvertersConfigurationInitializer()
        def grailsApplication = new DefaultGrailsApplication()
        initializer.grailsApplication = grailsApplication
        initializer.initialize()
    }

    void cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Album)
        ConvertersConfigurationHolder.clear()
        ShutdownOperations.runOperations()
    }

    void "Test including properties with JsonRenderer via RenderContext"() {
        given:"A new JsonRenderer instance is created that excludes properties"
            def renderer = new JsonRenderer(Album)
            renderer.registerCustomConverter()

        when:"The renderer renders an object"
            final webRequest = GrailsWebMockUtil.bindMockWebRequest()
            renderer.render(new Album(title: "Undertow", isbn: "38047301"), new ServletRenderContext(webRequest, [includes:['title']]))

        then:"Only included properties are rendered"
            webRequest.response.contentAsString == '{"title":"Undertow"}'

    }
    void "Test including properties with JsonRenderer"() {
        given:"A new JsonRenderer instance is created that excludes properties"
            def renderer = new JsonRenderer(Album)
            renderer.registerCustomConverter()
            renderer.includes =  ['title']

        when:"The renderer renders an object"
            final webRequest = GrailsWebMockUtil.bindMockWebRequest()
            renderer.render(new Album(title: "Undertow", isbn: "38047301"), new ServletRenderContext(webRequest))

        then:"Only included properties are rendered"
            webRequest.response.contentAsString == '{"title":"Undertow"}'

    }

    void "Test excluding properties with JsonRenderer"() {
        given:"A new JsonRenderer instance is created that excludes properties"
            def renderer = new JsonRenderer(Album)
            renderer.registerCustomConverter()
            renderer.excludes =  ['isbn']

        when:"The renderer renders an object"
            final webRequest = GrailsWebMockUtil.bindMockWebRequest()
            renderer.render(new Album(title: "Undertow", isbn: "38047301"), new ServletRenderContext(webRequest))

        then:"Only included properties are rendered"
            webRequest.response.contentAsString == '{"title":"Undertow"}'

    }

    void "Test render domain class with JsonRenderer"() {
        given:"A new JsonRenderer instance is created with the defaults"
        def renderer = new JsonRenderer(Song)
        def app = new DefaultGrailsApplication(Song)
        app.setConfig(new PropertySourcesConfig())
        def context = new KeyValueMappingContext("jsonrenderer")
        context.addPersistentEntities(Song)

        app.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> {
                context
            }
        })
        app.setMappingContext(context)
        app.initialise()
        renderer.grailsApplication = app
        renderer.registerCustomConverter()

        when:"The renderer renders an object"
        final webRequest = GrailsWebMockUtil.bindMockWebRequest()
        renderer.render(new Song(title: "Undertow"), new ServletRenderContext(webRequest))

        then:"Only included properties are rendered"
        webRequest.response.contentAsString == '{"title":"Undertow"}'

    }

    void "Test render domain class with JsonRenderer and including version and class"() {
        given:"A new JsonRenderer instance is created with the defaults"
        def renderer = new JsonRenderer(Song)
        renderer.includes = ['version', 'class', 'title']
        def app = new DefaultGrailsApplication(Song)
        app.setConfig(new PropertySourcesConfig())
        def context = new KeyValueMappingContext("jsonrenderer")
        context.addPersistentEntities(Song)

        app.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> {
                context
            }
        })
        app.setMappingContext(context)
        app.initialise()
        renderer.grailsApplication = app
        renderer.registerCustomConverter()

        when:"The renderer renders an object"
        final webRequest = GrailsWebMockUtil.bindMockWebRequest()
        renderer.render(new Song(title: "Undertow"), new ServletRenderContext(webRequest))

        then:"Only included properties are rendered"
        webRequest.response.contentAsString == '{"class":"org.grails.plugins.web.rest.render.json.Song","title":"Undertow"}'

    }
}

class Album {
    String title
    String isbn
}

@Entity
class Song {
    String title
}