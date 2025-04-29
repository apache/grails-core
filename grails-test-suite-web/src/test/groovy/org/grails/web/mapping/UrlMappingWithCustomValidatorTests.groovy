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
import grails.web.mapping.UrlMappingsHolder
import org.springframework.core.io.ByteArrayResource
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class UrlMappingWithCustomValidatorTests extends Specification implements GrailsWebUnitTest {

    def topLevelMapping = '''
mappings {
    "/help/$path**"(controller : "wiki", action : "show", id : "1") {
        constraints {
            path(validator : { val, obj -> ! val.startsWith("js") })
        }
    }
}
'''
    def UrlMappingsHolder holder

    void setup() {
        def res = new ByteArrayResource(topLevelMapping.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        holder = new DefaultUrlMappingsHolder(mappings)
    }


    void testMatchWithCustomValidator() {
        when:
        def info = holder.match("/help/foo.html")

        then:
        info

        when:
        info = holder.match("/help/js/foo.js")

        then:
        !info
    }
}
