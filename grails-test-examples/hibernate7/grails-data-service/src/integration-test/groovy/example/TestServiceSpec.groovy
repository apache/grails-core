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

package example

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

@Integration
@Rollback
class TestServiceSpec extends Specification {

    TestService testService
    TestBean testBean
    ClassUsingAService classUsingAService

    @Autowired
    List<BookService> bookServiceList

    void "test data-service is loaded correctly"() {
        when:
        classUsingAService.doSomethingWithTheService()

        and:
        testService.testDataService()

        then:
        noExceptionThrown()
    }

    void "test autowire by type"() {

        expect:
        testBean.bookRepo != null
    }

    void "test autowire by name works"() {

        expect:
        testBean.bookService != null
    }

    void "test that there is only one bookService"() {
        expect:
        bookServiceList.size() == 1
    }
}
