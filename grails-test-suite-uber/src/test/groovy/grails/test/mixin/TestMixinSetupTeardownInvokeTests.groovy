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
package grails.test.mixin

import spock.lang.Specification
import spock.lang.Stepwise

/**
 * @author Graeme Rocher
 */
@Stepwise
class TestMixinSetupTeardownInvokeTests extends Specification {

    static int counter = 1

    def value

    void setup() {
        value = 'World!'
    }

    void cleanup() {
        System.setProperty(TestMixinSetupTeardownInvokeTests.name, "invoked")
    }

    void testThatSetupWasInvoked() {
        println "invoked 1 ${counter++} ${TestMixinSetupTeardownInvokeTests.class.hashCode()}"

        expect:
        value == 'World!'
    }

    void testThatSetupWasInvoked2() {
        println "invoked 2 ${counter++} ${TestMixinSetupTeardownInvokeTests.class.hashCode()}"

        expect:
        System.getProperty(TestMixinSetupTeardownInvokeTests.name) == 'invoked'
    }
}
