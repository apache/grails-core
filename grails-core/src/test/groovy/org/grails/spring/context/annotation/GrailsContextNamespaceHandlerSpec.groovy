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
package org.grails.spring.context.annotation

import java.lang.reflect.Field

import org.springframework.beans.factory.xml.NamespaceHandlerSupport
import spock.lang.Specification

class GrailsContextNamespaceHandlerSpec extends Specification {

    void 'init registers a component-scan parser that ignores Groovy closure classes'() {
        given:
        GrailsContextNamespaceHandler handler = new GrailsContextNamespaceHandler()

        when:
        handler.init()

        then:
        registeredParsers(handler)['component-scan'] instanceof ClosureClassIgnoringComponentScanBeanDefinitionParser
    }

    private static Map registeredParsers(NamespaceHandlerSupport handler) {
        Field field = NamespaceHandlerSupport.getDeclaredField('parsers')
        field.accessible = true
        (Map) field.get(handler)
    }

}
