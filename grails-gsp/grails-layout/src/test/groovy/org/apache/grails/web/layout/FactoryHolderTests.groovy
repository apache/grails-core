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
package org.apache.grails.web.layout

import com.opensymphony.module.sitemesh.DecoratorMapper
import com.opensymphony.module.sitemesh.Factory
import com.opensymphony.module.sitemesh.PageParser
import spock.lang.Specification

class FactoryHolderTests extends Specification {

    void "get factory returns factory for non null factory"() {
        given:
        def factory = new DummyFactory()

        when:
        FactoryHolder.setFactory(factory)

        then:
        factory.is(FactoryHolder.getFactory())
    }

    void "get factory for null factory"() {
        when:
        FactoryHolder.setFactory(null)

        then:
        !FactoryHolder.getFactory()
    }

    // Silly test, but a necessary evil in order to get Cobertura to give us 100% coverage
    void "private constructor"() {
        expect:
        new FactoryHolder()
    }
}

/** A bare minimum implementation needed to test the factory above. */
class DummyFactory extends Factory {

    boolean isPathExcluded(String path) { false }

    boolean shouldParsePage(String contentType) { false }

    DecoratorMapper getDecoratorMapper() { null }

    void refresh() {}

    PageParser getPageParser(String contentType) { null }
}

