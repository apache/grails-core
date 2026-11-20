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

package gorm

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.*

@Integration(applicationClass = Application)
@Rollback
class BookSpec extends Specification {


    @Autowired
    TestService testService

    void "Test dynamic finders work"() {
        expect:"Dynamic finders to work"
            testService.testDynamicFinders() // test when called from service
            Book.count() == 1
            Book.findByTitle("The Stand")
            Book.where {
                title == "The Stand"
            }.count() == 1            
            Book.countByTitle("The Stand") == 1
            Book.countByTitle("Something Else") == 0
            Book.findAllByTitle("The Stand").size() == 1

    }

}
