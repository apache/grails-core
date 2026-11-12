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

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Rollback
@Integration
class SecuredControllerSpec extends ContainerGebSpec {

    def setup() {
        if (!User.findByUsername('sherlock')) {

            def sherlock = new User(username: 'sherlock', password: 'elementary').save(flush: true, failOnError: true)
            def watson = new User(username: 'watson', password: 'houndsofbaskerville').save(flush: true, failOnError: true)
            def detectives =  new RoleGroup(name: 'Detectives').save(flush: true, failOnError: true)
            def detectiveRole = new Role(authority: 'ROLE_ADMIN').save(flush: true, failOnError: true)

            new RoleGroupRole(roleGroup: detectives, role: detectiveRole).save(flush: true, failOnError: true)
            new UserRoleGroup(user: sherlock, roleGroup: detectives).save(flush: true, failOnError: true)
            new UserRoleGroup(user: watson, roleGroup: detectives).save(flush: true, failOnError: true)
        }
    }

    def 'test login as sherlock, sherlock belongs to detective groups. All detectives have the role ADMIN'() {
        when:
        via(SecuredPage)
        def page = at(LoginPage)

        and:
        page.login('sherlock', 'elementary')

        then:
        pageSource.contains('you have ROLE_ADMIN')

        and: 'User has not role assigned to him directly'
        UserRole.count() == 0

        cleanup: 'logout user, so next test can login with different user'
        browser.clearCookies()
    }

    def 'test login as watson, watson belongs to detective groups. All detectives have the role ADMIN'() {
        when:
        via(SecuredPage)
        def page = at(LoginPage)

        and:
        page.login('watson', 'houndsofbaskerville')

        then:
        pageSource.contains('you have ROLE_ADMIN')

        and: 'User has not role assigned to him directly'
        UserRole.count() == 0

        cleanup: 'logout user, so next test can login with different user'
        browser.clearCookies()
    }
}
