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
package org.grails.testing

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class GreetingHelper {
    String text = 'hello'
}

class GreetingService {
    GreetingHelper greetingHelper

    String greet() {
        greetingHelper.text
    }
}

class BeansBlockSpec extends Specification implements ServiceUnitTest<GreetingService> {

    def beans = {
        bean(GreetingHelper)
    }

    void "a spec's beans block registers its beans, with no annotation and no nested class written"() {
        expect: 'the service under test is wired with them'
        service.greet() == 'hello'

        and: 'they came from the class the block compiled to'
        configurationClasses*.name == ['org.grails.testing.BeansBlockSpec$BeansConfiguration']
    }
}

abstract class BeansBlockBaseSpec extends Specification implements GrailsUnitTest {

    def beans = {
        bean('inheritedGreeting', StringBuilder) {
            new StringBuilder('inherited')
        }

        bean('sharedGreeting', StringBuilder) {
            new StringBuilder('base')
        }
    }
}

class InheritedBeansBlockSpec extends BeansBlockBaseSpec {

    def beans = {
        bean('sharedGreeting', StringBuilder) {
            new StringBuilder('subclass')
        }
    }

    void "a base spec's beans block applies to the specs that extend it, and the subclass's wins a shared name"() {
        expect:
        applicationContext.getBean('inheritedGreeting').toString() == 'inherited'
        applicationContext.getBean('sharedGreeting').toString() == 'subclass'
    }
}
