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
package org.apache.grails.core.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import spock.lang.Specification

import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertyResolver

import grails.config.Config
import grails.config.ConfigMap
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager

/**
 * Covers the framework's own interfaces being callable from an image.
 *
 * <p>A plugin descriptor is Groovy written largely without static compilation, so reading a setting
 * or asking the application about its artefacts is resolved where the call is written and made
 * reflectively. An application never names these interfaces itself, so nothing else asks an image to
 * keep them, and a context stopped starting on ConfigMap.getProperty -- how nearly every plugin
 * reads its settings.</p>
 */
class GrailsApiRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new GrailsApiRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    private boolean invocable(Class<?> type) {
        def hint = hints.reflection().getTypeHint(TypeReference.of(type))
        hint != null && hint.memberCategories.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'the configuration a plugin reads its settings from can be called'() {
        expect:
            invocable(ConfigMap)
            invocable(Config)
    }

    void 'what a plugin asks about the application can be called'() {
        expect:
            invocable(GrailsApplication)
    }

    void 'what a plugin asks about the plugins can be called'() {
        expect:
            invocable(GrailsPluginManager)
    }

    void 'what Spring hands a plugin can be called'() {
        expect: 'a descriptor is given these and calls them the same dynamic way'
            invocable(PropertyResolver)
            invocable(Environment)
            invocable(ApplicationContext)
    }

    void 'the call that stopped a context from starting is covered'() {
        given: 'the form a plugin uses to read a setting with a type and a default'
            def getProperty = ConfigMap.getMethod('getProperty', String, Class, Object)

        expect:
            getProperty.declaringClass == ConfigMap
            invocable(ConfigMap)
    }
}
