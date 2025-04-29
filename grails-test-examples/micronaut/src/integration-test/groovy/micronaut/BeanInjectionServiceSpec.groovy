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

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

@Integration
class BeanInjectionServiceSpec extends Specification {

    @Autowired
    BeanInjectionService beanInjectionService

    @Autowired
    TestService service

    @Autowired
    List<BeanInjectionService> bInjectService

    void "test injecting Micronaut beans in a Grails service"() {
        expect:
        beanInjectionService.namedServices.size() == 4
        beanInjectionService.namedService2.name == "regular"
        beanInjectionService.namedService3.name == "special"
        beanInjectionService.namedService4.name == "qualified"
        beanInjectionService.namedService.name == "primary"
    }

    void "test autowire Grails service by type"() {
        expect:
        service != null
    }

    void "test there are two beans for a Grails service when we inject Micronaut bean in it"() {
        expect:
        bInjectService != null
        bInjectService.size() == 2
    }
}
