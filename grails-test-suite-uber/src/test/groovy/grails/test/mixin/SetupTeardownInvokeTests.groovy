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

import grails.testing.web.controllers.ControllerUnitTest
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import spock.lang.Specification

/**
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SetupTeardownInvokeTests extends Specification implements ControllerUnitTest<SetupTeardownController> {
    static int counter=1

    void setup() {
        controller.value = 'World!'
    }

    void cleanup() {
        controller.counter++
    }

    void testThatSetupWasInvoked() {
        println "invoked 1 ${counter++} ${SetupTeardownController.class.hashCode()}"
        expect:
        controller.value == 'World!'
    }

    void testThatSetupWasInvoked2() {
        println "invoked 2 ${counter++} ${SetupTeardownController.class.hashCode()}"
        expect:
        controller.counter == 1
    }
}

class SetupTeardownController {
    String value
    static int counter = 0
}
