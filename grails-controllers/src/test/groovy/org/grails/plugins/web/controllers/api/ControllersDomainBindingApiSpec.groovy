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
package org.grails.plugins.web.controllers.api

import spock.lang.Specification

class ControllersDomainBindingApiSpec extends Specification {

    void 'initialize with no bound GrailsApplication does not throw and leaves the instance untouched'() {
        given:
        BindingTarget target = new BindingTarget()

        when:
        ControllersDomainBindingApi.initialize(target)

        then:
        noExceptionThrown()
        target.name == null
    }

    void 'initialize with named args and no bound GrailsApplication falls back to plain object binding'() {
        given:
        BindingTarget target = new BindingTarget()

        when:
        ControllersDomainBindingApi.initialize(target, [name: 'Bob', age: 42])

        then:
        target.name == 'Bob'
        target.age == 42
    }

    void 'AUTOWIRE_DOMAIN_METHOD constant is stable'() {
        expect:
        ControllersDomainBindingApi.AUTOWIRE_DOMAIN_METHOD == 'autowireDomain'
    }

}

class BindingTarget {

    String name
    Integer age

}
