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
class UserSpec extends Specification {

    @Autowired
    UserService userService

    void "Test where query over association id works"() {
        given: "Cities and users are set up"
            // Create test data within the test to ensure it exists
            def london = City.findByName("London") ?: new City(name: "London")
            if (!london.id) {
                london.addToUsers(name: "Bob")
                london.addToUsers(name: "Fred")
                london.save(flush: true)
            }

            def paris = City.findByName("Paris") ?: new City(name: "Paris")
            if (!paris.id) {
                paris.addToUsers(name: "Joe")
                paris.save(flush: true)
            }

        when: "An association is queried with a where query"
            def results = userService.bycity(london.id)

        then: "The results are correct"
            london.id != null
            results.size() == 2
            results*.name.containsAll(['Bob', 'Fred'])
    }
}
