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
package org.grails.commons

import grails.core.DefaultArtefactInfo
import org.grails.core.DefaultGrailsControllerClass
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultArtefactInfoTests {

    @Test
    void testAddGrailsClass() {
        def info = new DefaultArtefactInfo()

        def gcl = new GroovyClassLoader()

        info.updateComplete()

        assertEquals 0, info.classes.size()
        assertEquals 0, info.classesByName.size()
        assertEquals 0, info.grailsClasses.size()
        assertEquals 0, info.grailsClassesByName.size()
        assertEquals 0, info.grailsClassesArray.size()

        def c1 = gcl.parseClass('''
class FooController {}
''')
        def controllerClass1 = new DefaultGrailsControllerClass(c1)
        info.addGrailsClass(controllerClass1)

        info.updateComplete()

        assertEquals 1, info.classes.size()
        assertEquals 1, info.classesByName.size()
        assertEquals 1, info.grailsClasses.size()
        assertEquals 1, info.grailsClassesByName.size()
        assertEquals 1, info.grailsClassesArray.size()

        def c2 = gcl.parseClass('''
class BarController {}
''')
        def controllerClass2 = new DefaultGrailsControllerClass(c2)
        info.addGrailsClass(controllerClass2)

        info.addGrailsClass(controllerClass2)

        info.updateComplete()

        assertEquals 2, info.classes.size()
        assertEquals 2, info.classesByName.size()
        assertEquals 2, info.grailsClasses.size()
        assertEquals 2, info.grailsClassesByName.size()
        assertEquals 2, info.grailsClassesArray.size()

        // test class of same name
        def c3 = gcl.parseClass('''
class FooController {}
''')
        def controllerClass3 = new DefaultGrailsControllerClass(c3)
        info.addGrailsClass(controllerClass3)

        info.updateComplete()

        assertEquals 2, info.classes.size()
        assertEquals 2, info.classesByName.size()
        assertEquals 2, info.grailsClasses.size()
        assertEquals 2, info.grailsClassesByName.size()
        assertEquals 2, info.grailsClassesArray.size()
    }
}
