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
package org.grails.domain.compiler

import grails.persistence.Entity
import org.grails.plugins.web.controllers.api.ControllersDomainBindingApi
import spock.lang.Specification

class DomainPropertiesAccessorSpec extends Specification {

    void "Test binding constructor adding via AST"() {
        when:
            def test = new TestDomain(age: 10)

        then:
            test.age == 10
    }

    void "Test setProperties method added via AST"() {
        when:
            def test = new TestDomain()
            test.properties = [age: 10]

        then:
            test.age == 10
    }

    void "Test getProperties method added via AST"() {
        when:
            def test = new TestDomain()
            test.properties['age', 'name'] = [age: 10]

        then:
            test.age == 10
    }
}

@Entity
class TestDomain {
    Integer age
}

