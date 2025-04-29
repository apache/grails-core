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
package demo

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class DefineBeansSpec extends Specification implements ServiceUnitTest<ReportingService> {

    void "test dependency injection with defineBeans"() {
        given:
        defineBeans {
            someHelper RushHelper
        }

        expect:
        service.retrieveSomeNumber() == 2112
    }

    // tag::test_declaration[]
    void "test the bean is available to the context"() {
        given:
        defineBeans {
            someInteger(Integer, 2)
        }

        expect:
        applicationContext.getBean('someInteger') == 2
    }
    // end::test_declaration[]
}
