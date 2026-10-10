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
package org.grails.core.support

import spock.lang.Specification

import java.net.URLClassLoader

class ClassEditorSpec extends Specification {

    void 'setAsText resolves a regular class by name'() {
        given:
        def editor = new ClassEditor()

        when:
        editor.asText = 'java.lang.String'

        then:
        editor.value == String
        editor.asText == 'java.lang.String'
    }

    void 'setAsText resolves a primitive class name'() {
        given:
        def editor = new ClassEditor()

        when:
        editor.asText = 'int'

        then:
        editor.value == int
    }

    void 'setAsText with an unresolvable name throws IllegalArgumentException'() {
        given:
        def editor = new ClassEditor()

        when:
        editor.asText = 'not.a.real.ClassName'

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains('not.a.real.ClassName')
    }

    void 'a custom classloader is used to resolve the class'() {
        given:
        def editor = new ClassEditor()
        boolean loaded = false
        def cl = new ClassLoader(Thread.currentThread().contextClassLoader) {
            @Override
            Class<?> loadClass(String name) throws ClassNotFoundException {
                loaded = true
                super.loadClass(name)
            }
        }
        editor.setClassLoader(cl)

        when:
        editor.asText = 'java.lang.String'

        then:
        loaded
        editor.value == String
    }

    void 'setClassLoader with null does not clear a previously set classloader'() {
        given:
        def editor = new ClassEditor()
        def cl = new URLClassLoader([] as URL[], Thread.currentThread().contextClassLoader)
        editor.setClassLoader(cl)

        when:
        editor.setClassLoader(null)
        editor.asText = 'java.lang.String'

        then:
        editor.value == String
    }
}
