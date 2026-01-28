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

package micronaut

import bean.injection.PrimaryNamedService
import bean.injection.QualifiedNamedService
import bean.injection.RegularNamedService
import bean.injection.SpecialNamedService
import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration

/**
 * Integration tests for Micronaut bean qualifiers in Grails context.
 * 
 * Tests that:
 * 1. @Named qualifier works correctly
 * 2. @Primary qualifier works correctly
 * 3. Custom qualifier annotations work
 * 4. Collection injection with multiple implementations works
 */
@Integration
class MicronautQualifierSpec extends Specification {

    @Autowired
    BeanInjectionService beanInjectionService

    void "primary bean is injected when no qualifier specified"() {
        expect:
        beanInjectionService.namedService != null
        beanInjectionService.namedService.name == 'primary'
        beanInjectionService.namedService instanceof PrimaryNamedService
    }

    void "@Named('regular') qualifier injects correct bean"() {
        expect:
        beanInjectionService.namedService2 != null
        beanInjectionService.namedService2.name == 'regular'
        beanInjectionService.namedService2 instanceof RegularNamedService
    }

    void "@Named('special') qualifier injects correct bean"() {
        expect:
        beanInjectionService.namedService3 != null
        beanInjectionService.namedService3.name == 'special'
        beanInjectionService.namedService3 instanceof SpecialNamedService
    }

    void "custom @Qualified annotation injects correct bean"() {
        expect:
        beanInjectionService.namedService4 != null
        beanInjectionService.namedService4.name == 'qualified'
        beanInjectionService.namedService4 instanceof QualifiedNamedService
    }

    void "collection injection includes all implementations"() {
        expect:
        beanInjectionService.namedServices != null
        beanInjectionService.namedServices.size() == 4

        and: "all implementations are present"
        beanInjectionService.namedServices.any { it.name == 'primary' }
        beanInjectionService.namedServices.any { it.name == 'regular' }
        beanInjectionService.namedServices.any { it.name == 'special' }
        beanInjectionService.namedServices.any { it.name == 'qualified' }
    }

    void "each bean implementation returns unique name"() {
        when:
        def names = beanInjectionService.namedServices*.name.unique()

        then:
        names.size() == 4
        names.containsAll(['primary', 'regular', 'special', 'qualified'])
    }

    void "beans can be distinguished by their class type"() {
        when:
        def types = beanInjectionService.namedServices*.getClass()*.simpleName

        then:
        types.contains('PrimaryNamedService')
        types.contains('RegularNamedService')
        types.contains('SpecialNamedService')
        types.contains('QualifiedNamedService')
    }
}
