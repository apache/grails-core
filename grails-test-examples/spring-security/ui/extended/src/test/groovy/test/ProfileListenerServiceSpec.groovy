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

package test

import grails.plugin.springsecurity.SpringSecurityService
import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.springframework.test.annotation.Rollback
import spock.lang.Specification

class ProfileListenerServiceSpec extends Specification implements ServiceUnitTest<ProfileListenerService>, DataTest {

    def setupSpec() {
        mockDomain(Profile)
        mockDomain(User)
    }

    Closure doWithSpring() {{ -> // <1>
        springSecurityService(SpringSecurityService)
    }}

    @Rollback
    void 'test my answer is encoded on insert'() {
        given:
        User u = new User(
                username: 'adminTest',
                password: 'pass',
                email: 'test@user.com',
                accountLocked: false,
                accountExpired: false
        )
        Profile prof = new Profile(
                myQuestion2: 'Practical Grails 3',
                myQuestion1: 'Eric Helgeson',
                myAnswer2: '12345',
                myAnswer1: '1234',
                user: u
        )

        when:
        service.springSecurityService = Stub(SpringSecurityService) {
            encodePassword(_ as String) >> 'XXXX-5125'
        }

        service.onProfilePreInsert(new PreInsertEvent(datastore, prof))

        then:
        prof.myAnswer1 == 'XXXX-5125'
        prof.myAnswer2 == 'XXXX-5125'
    }

    @Rollback
    void 'test my answer is encoded on update'() {
        given:
        User u = new User(
                username: 'adminTest',
                password: 'pass',
                email: 'test@user.com',
                accountLocked: false,
                accountExpired: false
        )
        Profile prof = new Profile(
                myQuestion2: 'Practical Grails 3',
                myQuestion1: 'Eric Helgeson',
                myAnswer2: '12345',
                myAnswer1: '1234',
                user: u
        )

        when:
        service.springSecurityService = Stub(SpringSecurityService) {
            encodePassword(_ as String) >> { args ->
                args[0] + 'XXXX-5125'
            }
        }

        prof.save(flush: true)
        service.onProfilePreInsert(new PreInsertEvent(datastore, prof))

        prof.myAnswer2 = '1232321344'
        service.onProfilePreUpdate(new PreUpdateEvent(datastore, prof))

        then:
        prof.myAnswer2 == '1232321344XXXX-5125'
        prof.myAnswer1 == '1234XXXX-5125'

    }
}
