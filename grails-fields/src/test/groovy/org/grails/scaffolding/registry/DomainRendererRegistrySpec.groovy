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
package org.grails.scaffolding.registry

import org.grails.scaffolding.model.property.DomainProperty
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by Jim on 5/26/2016.
 */
class DomainRendererRegistrySpec extends Specification {

    @Shared
    DomainOutputRendererRegistry registry

    void setup() {
        registry = new DomainOutputRendererRegistry()
    }

    void "test renderers are returned in order"() {
        given:
        DomainOutputRenderer levelOne = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        DomainOutputRenderer levelTwo = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        registry.registerDomainRenderer(levelOne, 1)
        registry.registerDomainRenderer(levelTwo, 2)

        when:
        DomainOutputRenderer resolved = registry.get(Mock(DomainProperty))

        then:
        resolved == levelTwo
    }

    void "test the last renderer added will have priority over others with the same priority"() {
        given:
        DomainOutputRenderer levelOne = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        DomainOutputRenderer levelTwo = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        registry.registerDomainRenderer(levelOne, 1)
        registry.registerDomainRenderer(levelTwo, 1)

        when:
        DomainOutputRenderer resolved = registry.get(Mock(DomainProperty))

        then:
        resolved == levelTwo
    }

    void "test only supported renderers are resolved"() {
        given:
        DomainOutputRenderer levelOne = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> true
        }
        DomainOutputRenderer levelTwo = Stub(DomainOutputRenderer) {
            supports(_ as DomainProperty) >> false
        }
        registry.registerDomainRenderer(levelOne, 1)
        registry.registerDomainRenderer(levelTwo, 2)

        when:
        DomainOutputRenderer resolved = registry.get(Mock(DomainProperty))

        then:
        resolved == levelOne
    }
}
