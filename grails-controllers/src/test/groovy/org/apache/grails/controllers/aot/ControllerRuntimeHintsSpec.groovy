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
package org.apache.grails.controllers.aot

import grails.artefact.controller.support.AllowedMethodsHelper

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import spock.lang.Specification

/**
 * Covers the controller API surviving into an ahead-of-time image. Without these hints the methods
 * are stripped and dispatch fails on the request that first takes the path, which for the method
 * check below means the first form submission rather than start-up.
 */
class ControllerRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new ControllerRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    private boolean registered(Class<?> type) {
        registered(TypeReference.of(type))
    }

    private boolean registered(TypeReference type) {
        def hint = hints.reflection().getTypeHint(type)
        hint != null && hint.memberCategories.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'the method check a form submission reaches is registered'() {
        expect: 'reached only by POST, PUT and DELETE, so a walk of an application never records it'
            registered(AllowedMethodsHelper)
    }

    void 'the controller trait Groovy dispatches through is registered'() {
        expect:
            registered(grails.artefact.Controller)
    }

    void 'the response and forwarding support types are registered'() {
        expect:
            registered(grails.artefact.controller.support.ResponseRenderer)
            registered(grails.artefact.controller.support.ResponseRedirector)
            registered(grails.artefact.controller.support.RequestForwarder)
    }

    void 'a type from a module that is absent is skipped rather than failing the build'() {
        given:
            RuntimeHints empty = new RuntimeHints()

        when: 'a class loader that resolves nothing, as an application without that plugin has'
            new ControllerRuntimeHints().registerHints(empty, new URLClassLoader(new URL[0], null))

        then:
            noExceptionThrown()

        and: 'the type it could not resolve contributes no hint'
            empty.reflection().getTypeHint(
                    TypeReference.of('grails.artefact.controller.RestResponder')) == null

        and: 'while the types this module declares are registered whatever a class loader can find'
            empty.reflection().getTypeHint(TypeReference.of(AllowedMethodsHelper)) != null
    }
}
