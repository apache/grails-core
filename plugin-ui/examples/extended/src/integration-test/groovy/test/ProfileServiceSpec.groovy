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

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import org.hibernate.SessionFactory

@Rollback
@Integration
class ProfileServiceSpec extends Specification {

    ProfileService profileService
    SessionFactory sessionFactory

    void 'test get'() {
        expect:
        profileService.get(1) != null
    }

    void 'test list'() {
        when:
        def profileList = profileService.list(max: 2, offset: 2)

        then:
        profileList.size() == 2
    }

    void 'test count'() {
        expect:
        profileService.count() == 4
    }

    void 'test delete'() {
        expect:
        profileService.count() == 4

        when:
        profileService.delete(Profile.first().id)
        sessionFactory.currentSession.flush()

        then:
        profileService.count() == 3
    }

    void 'test save'() {
        when:
        def profile = new Profile(
                user: User.findByUsername('foon_2'),
                myAnswer1: '1234',
                myQuestion1: 'Count to four',
                myAnswer2: '12345',
                myQuestion2: 'Count to Five'
        )
        profileService.save(profile)

        then:
        profile.id != null
    }
}
