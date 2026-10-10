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
package org.grails.web.converters.json.legacy

import com.fasterxml.jackson.annotation.JsonValue

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.support.proxy.EntityProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.persistence.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer

import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext

/**
 * Domain class instances, and the ways the JSON converter renders them, whose JSON Grails 8.0.x rendered. The same
 * scenarios ran on Grails 8.0.x (232bb34006), in UTC, to give the text that {@link Grails8JsonRenderingSpec} expects.
 */
class Grails8DomainRendering {

    /**
     * Renders every scenario with converters configured by the settings, and an application context that holds
     * the beans.
     *
     * @param settings converter settings, in addition to the scenario's own
     * @param beans the beans of the application context, by type
     * @return the JSON of each scenario
     */
    static List<String> renderAll(Map<String, Object> settings = [:], Map<Class, Object> beans = [:]) {
        [
                render(settings, beans) { new JSON(novel()).toString() },
                render(settings + ['grails.converters.json.domain.include.version': true,
                                   'grails.converters.json.domain.include.class': true], beans) {
                    new JSON(novel()).toString()
                },
                render(settings, beans) { JSON.use('deep') { new JSON(novel()).toString() } },
                render(settings + ['grails.converters.json.default.deep': true], beans) { new JSON(novel()).toString() },
                render(settings, beans) {
                    def json = new JSON(novel())
                    json.setIncludes(Novel, ['title', 'author', 'chapters'])
                    json.toString()
                },
                render(settings, beans) {
                    def json = new JSON(novel())
                    json.setExcludes(Novel, ['price', 'chapters', 'cover'])
                    json.toString()
                },
                render(settings + ['grails.converters.json.domain.include.version': true], beans) {
                    new JSON(new Novel(title: 'Unsaved')).toString()
                },
                render(settings, beans) { new JSON(new NovelProxy(target: novel(), proxyId: 1L)).toString() },
                render(settings, beans) { new JSON([novel(), new Author(name: 'Unsaved')]).toString() },
                render(settings, beans) { new JSON([(String): 'class', (new Date(0)): 'date', (new Code('k')): 'code']).toString() },
                render(settings, beans) { new JSON([code: new Code('k'), cover: new Cover(colour: 'red', pages: 12)]).toString() }
        ]
    }

    static Novel novel() {
        def author = new Author(name: 'Ursula')
        author.id = 7
        def editor = new Author(name: 'Virginia')
        editor.id = 8
        def first = new Chapter(heading: 'One')
        first.id = 1
        def second = new Chapter(heading: 'Two')
        second.id = 2
        def novel = new Novel(title: 'The Dispossessed', published: new Date(1790305200000L), role: Role.HEAD,
                price: new BigDecimal('12.50'), author: author, editor: new AuthorProxy(target: editor, proxyId: 8L),
                chapters: [first, second], chaptersByName: [one: first], cover: new Cover(colour: 'blue', pages: 387))
        novel.id = 1
        novel.version = 3
        novel
    }

    private static String render(Map<String, Object> settings, Map<Class, Object> beans, Closure<String> scenario) {
        GrailsApplication grailsApplication = domainApplication(settings)
        GenericApplicationContext context = new GenericApplicationContext()
        context.registerBean(ProxyHandler, { new NovelProxyHandler() })
        beans.each { Class type, Object bean -> context.registerBean(type, { bean }) }
        context.refresh()
        // the marshallers of an earlier configuration would be carried over
        ConvertersConfigurationHolder.clear()
        try {
            new ConvertersConfigurationInitializer(grailsApplication: grailsApplication, applicationContext: context)
                    .initialize()
            scenario.call()
        }
        finally {
            ConvertersConfigurationHolder.clear()
            context.close()
        }
    }

    private static GrailsApplication domainApplication(Map<String, Object> settings) {
        def grailsApplication = new DefaultGrailsApplication(Novel, Author, Chapter)
        // the configuration is created now, rather than from the application context set below
        def config = grailsApplication.config
        settings.each { String key, Object value -> config.setAt(key, value) }
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('json')
        mappingContext.addPersistentEntities(Novel, Author, Chapter)
        grailsApplication.setApplicationContext([getBean: { String name, Class type -> mappingContext }] as ApplicationContext)
        grailsApplication.setMappingContext(mappingContext)
        grailsApplication
    }
}

@Entity
class Novel {
    static hasMany = [chapters: Chapter, chaptersByName: Chapter]
    static embedded = ['cover']
    String title
    Date published
    Role role
    BigDecimal price
    Author author
    Author editor
    Author translator
    List chapters
    Map chaptersByName
    Cover cover
}

@Entity
class Author {
    String name
}

@Entity
class Chapter {
    String heading
}

/**
 * A map key whose JSON value differs from its {@code toString()}.
 */
class Code {
    final String code

    Code(String code) { this.code = code }

    @JsonValue
    String jsonValue() { "json:$code" }

    @Override
    String toString() { "code:$code" }
}

class Cover {
    String colour
    Integer pages
}

class NovelProxy extends Novel {
    Novel target
    Long proxyId
}

class AuthorProxy extends Author {
    Author target
    Long proxyId
}

class NovelProxyHandler implements EntityProxyHandler {

    boolean isProxy(Object o) { o instanceof NovelProxy || o instanceof AuthorProxy }

    Object unwrapIfProxy(Object instance) {
        if (instance instanceof NovelProxy) {
            return instance.target
        }
        if (instance instanceof AuthorProxy) {
            return instance.target
        }
        instance
    }

    boolean isInitialized(Object o) { !(o instanceof AuthorProxy) }

    boolean isInitialized(Object obj, String associationName) { true }

    void initialize(Object o) {}

    Object getProxyIdentifier(Object o) {
        o instanceof NovelProxy ? o.proxyId : o instanceof AuthorProxy ? o.proxyId : null
    }

    Class<?> getProxiedClass(Object o) {
        o instanceof NovelProxy ? Novel : o instanceof AuthorProxy ? Author : o.getClass()
    }
}
