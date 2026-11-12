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
package grails.gsp.boot

import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.support.GenericApplicationContext

import grails.artefact.Artefact
import grails.gsp.TagLib
import org.grails.web.pages.StandaloneTagLibraryLookup

import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Which tag libraries a Spring Boot application can render a page with. It has no plugins to scan
 * for artefacts, so a tag library reaches a page by being a bean of the context - whether it was
 * written for a Spring Boot application or comes out of a Grails plugin, as the asset pipeline's
 * {@code <asset:...>} library does.
 */
class StandaloneTagLibraryLookupSpec extends Specification {

    @AutoCleanup
    private final GenericApplicationContext context = new GenericApplicationContext()

    void setup() {
        context.registerBeanDefinition('gspTagLibraryLookup', new RootBeanDefinition(StandaloneTagLibraryLookup))
    }

    void 'a tag library written for a Spring Boot application is looked up by its tag'() {
        given:
        register('bootTagLib', BootTagLib)

        when:
        context.refresh()

        then:
        lookup().lookupTagLibrary('boot', 'hello') instanceof BootTagLib
    }

    void 'a tag library that comes out of a Grails plugin is looked up by its tag'() {
        given: 'a tag library marked the way a plugin marks one, rather than with @TagLib'
        register('pluginTagLib', PluginTagLib)

        when:
        context.refresh()

        then:
        lookup().lookupTagLibrary('plugin', 'hello') instanceof PluginTagLib
    }

    void 'an artefact of another kind is no tag library'() {
        given:
        register('serviceArtefact', ServiceArtefact)

        when:
        context.refresh()

        then:
        !lookup().hasNamespace('service')
    }

    private void register(String name, Class<?> type) {
        context.registerBeanDefinition(name, new RootBeanDefinition(type))
    }

    private StandaloneTagLibraryLookup lookup() {
        context.getBean('gspTagLibraryLookup', StandaloneTagLibraryLookup)
    }

}

@TagLib
class BootTagLib {

    static String namespace = 'boot'

    def hello(Map attrs) {
    }

}

@Artefact('TagLib')
class PluginTagLib {

    static String namespace = 'plugin'

    def hello(Map attrs) {
    }

}

@Artefact('Service')
class ServiceArtefact {

    static String namespace = 'service'

    def hello(Map attrs) {
    }

}
