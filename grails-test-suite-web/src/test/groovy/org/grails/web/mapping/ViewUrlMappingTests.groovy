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
package org.grails.web.mapping

import grails.testing.web.GrailsWebUnitTest
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import spock.lang.Specification
import org.springframework.core.io.ByteArrayResource

/**
 * @author mike
 */
class ViewUrlMappingTests extends Specification implements GrailsWebUnitTest {

    def topLevelMapping = '''
mappings {
  "/book/$author/$title" {
    view="book.gsp"
  }
  "/book2/foo"(view:"book.gsp")
  "/book3"(controller:"book", view:"list")
}
'''
    def UrlMappingsHolder holder

    void setup() {
        def res = new ByteArrayResource(topLevelMapping.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        holder = new DefaultUrlMappingsHolder(mappings)
    }

    void testParse() {
        expect:
        holder != null
    }

    void testMatch() {
        when:
        UrlMappingInfo info = holder.match("/book/joyce/ullisses")

        then:
        "book.gsp" == info.getViewName()
    }

    void testMatch2() {
        when:
        UrlMappingInfo info = holder.match("/book2/foo")

        then:
        "book.gsp" == info.getViewName()
    }

    void testMatchToControllerAndView() {
        when:
        UrlMappingInfo info = holder.match("/book3")

        then:
        "list" == info.viewName
        "book" == info.controllerName
    }
}
