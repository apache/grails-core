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
package org.grails.plugins.web.rest.transform

import grails.web.Action
import groovy.transform.Generated
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * @author Graeme Rocher
 */
class LinkableTransformSpec extends Specification {

    void "Test that the resource transform creates a controller class"() {
        given:"A parsed class with a @Resource annotation"
            def gcl = new GroovyClassLoader()
            gcl.parseClass('''
    import grails.rest.*
    import grails.persistence.*

    @Linkable
    class Book {
    }
    ''')

        when:"A link is added"
            def domain = gcl.loadClass("Book")
            def book = domain.getDeclaredConstructor().newInstance()
            book.link(rel:'foos', href:"/foo")
            def links = book.links()

        then:"The link is added to the available links"
            links[0].href == '/foo'

        when: "find all added methods"
            List<Method> addedLinkMethods = book.getClass().getMethods().findAll {it.name == 'link' || it.name == 'links'}
        then: "they are marked as Generated"
            addedLinkMethods.each {
                assert it.isAnnotationPresent(Generated)
            }
    }
}
