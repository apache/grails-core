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
package org.grails.compiler.injection

import grails.persistence.Entity
import groovy.transform.Generated
import groovy.transform.ToString
import spock.lang.Specification

/**
 * @author James Kleeh
 */
class DefaultDomainClassInjectorSpec extends Specification {

    void "test default toString"() {
        when:
        Test test = new Test()
        test.id = 1

        then:
        test.toString().endsWith("Test : 1")

        and: 'toString is marked as Generated'
        test.class.getMethod('toString').isAnnotationPresent(Generated)
    }

    void "test domain with groovy.transform.ToString"() {
        when:
        TestWithGroovy test = new TestWithGroovy()
        test.id = 1

        then:
        test.toString().endsWith("TestWithGroovy(1)")

        and: 'toString is marked as Generated'
        test.class.getMethod('toString').isAnnotationPresent(Generated)
    }

    @Entity
    class Test {
    }

    @Entity
    @ToString(includes = ["id"])
    class TestWithGroovy {
    }
}
