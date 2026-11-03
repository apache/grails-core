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
package org.grails.web.servlet.boostrap

import spock.lang.Specification

class DefaultGrailsBootstrapClassSpec extends Specification {

    protected GroovyClassLoader gcl = new GroovyClassLoader()

    void cleanup() {
        gcl.clearCache()
    }

    void 'the constructor caches a single reference instance and always returns it'() {
        given:
        Class bootstrapClass = gcl.parseClass('class DgbcPlain { }')

        when:
        DefaultGrailsBootstrapClass grailsClass = new DefaultGrailsBootstrapClass(bootstrapClass)

        then:
        grailsClass.referenceInstance.is(grailsClass.referenceInstance)
        DefaultGrailsBootstrapClass.BOOT_STRAP == 'BootStrap'
    }

    void 'init and destroy closures are invoked when declared and are no-ops otherwise'() {
        given:
        List events = []
        Class bootstrapClass = gcl.parseClass('''
            class DgbcWithClosures {
                def events = []
                def init = { events << "init" }
                def destroy = { events << "destroy" }
            }
        ''')
        DefaultGrailsBootstrapClass grailsClass = new DefaultGrailsBootstrapClass(bootstrapClass)
        Class emptyClass = gcl.parseClass('class DgbcEmpty { }')
        DefaultGrailsBootstrapClass emptyGrailsClass = new DefaultGrailsBootstrapClass(emptyClass)

        expect:
        grailsClass.initClosure instanceof Closure
        grailsClass.destroyClosure instanceof Closure
        emptyGrailsClass.initClosure instanceof Closure
        emptyGrailsClass.destroyClosure instanceof Closure

        when:
        grailsClass.callInit()
        grailsClass.callDestroy()
        emptyGrailsClass.callInit()
        emptyGrailsClass.callDestroy()

        then:
        grailsClass.referenceInstance.events == ['init', 'destroy']
        noExceptionThrown()
    }

    void 'a non-closure init or destroy property is treated as absent'() {
        given:
        Class bootstrapClass = gcl.parseClass('''
            class DgbcWrongType {
                def init = 'not a closure'
            }
        ''')

        when:
        DefaultGrailsBootstrapClass grailsClass = new DefaultGrailsBootstrapClass(bootstrapClass)
        grailsClass.callInit()

        then:
        noExceptionThrown()
        grailsClass.initClosure.call() == null
    }

}
