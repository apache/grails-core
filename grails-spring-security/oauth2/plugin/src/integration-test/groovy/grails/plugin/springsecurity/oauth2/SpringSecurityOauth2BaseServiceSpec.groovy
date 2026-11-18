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
package grails.plugin.springsecurity.oauth2

import spock.lang.Specification

import org.springframework.transaction.PlatformTransactionManager

import grails.testing.services.ServiceUnitTest

class SpringSecurityOauth2BaseServiceSpec extends Specification implements ServiceUnitTest<SpringSecurityOauth2BaseService> {

    void setup() {
        // The service is @Transactional; no GORM implementation is set up in a unit test
        service.transactionManager = Stub(PlatformTransactionManager)
    }

    void 'a newly registered user gets ROLE_USER when no role names are configured'() {
        expect:
        service.roleNames == ['ROLE_USER']
    }

    void 'a newly registered user gets the configured role names'() {
        given:
        config.merge([grails: [plugin: [springsecurity: [oauth2: [registration: [roleNames: ['ROLE_A', 'ROLE_B']]]]]]])

        expect:
        service.roleNames == ['ROLE_A', 'ROLE_B']
    }
}
