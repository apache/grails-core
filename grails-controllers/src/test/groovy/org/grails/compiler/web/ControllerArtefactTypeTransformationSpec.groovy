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

package org.grails.compiler.web

import grails.artefact.Enhanced
import grails.web.Controller
import org.grails.compiler.injection.GrailsAwareClassLoader

import spock.lang.Specification

/**
 * Compiles classes annotated with the {@code @Controller} shortcut to prove it resolves to the
 * Controller artefact type.
 */
class ControllerArtefactTypeTransformationSpec extends Specification {

    void 'the Controller annotation marks the compiled class as a Controller artefact'() {
        given:
        def gcl = new GrailsAwareClassLoader()

        when:
        Class cls = gcl.parseClass('''
            @grails.web.Controller
            class ShortcutController {
                def index() { 'ok' }
            }
        ''')

        then: 'the controller injectors ran, which only happens for the Controller artefact type'
        cls.isAnnotationPresent(Controller)
        cls.getAnnotation(Enhanced).enhancedFor().toList() == ['allowedMethods']
    }
}
