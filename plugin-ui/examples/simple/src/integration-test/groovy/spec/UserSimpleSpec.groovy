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

package spec

import grails.testing.mixin.integration.Integration
import page.user.UserCreatePage
import page.user.UserEditPage
import page.user.UserSearchPage
import spock.lang.Issue

@Integration
class UserSimpleSpec extends AbstractSecuritySpec {

    void testFindAll() {
        when:
        def userSearchPage = browser.to(UserSearchPage)

        then:
        userSearchPage.assertNotSearched()

        when:
        userSearchPage.submit()

        then:
        browser.at(UserSearchPage)
        userSearchPage.assertResults(1, 10, 22)
    }

    void testFindByUsername() {
        when:
        def userSearchPage = browser.to(UserSearchPage).tap {
            username = 'foo'
            submit()
        }

        then:
        browser.at(UserSearchPage)
        userSearchPage.assertResults(1, 3, 3)

        assertContentContains('foon_2')
        assertContentContains('foolkiller')
        assertContentContains('foostra')
    }

    void testFindByDisabled() {
        when:
        def userSearchPage = browser.to(UserSearchPage)

        // Temporary workaround for problem with Geb RadioButtons module
        //userSearchPage.enabled.checked = '-1'
        browser.$('input', type: 'radio', name: 'enabled', value: '-1').click()
        userSearchPage.submit()

        then:
        browser.at(UserSearchPage)
        userSearchPage.assertResults(1, 1, 1)
        assertContentContains('billy9494')
    }

    void testFindByAccountExpired() {
        when:
        def userSearchPage = browser.to(UserSearchPage)

        // Temporary workaround for problem with Geb RadioButtons module
        //userSearchPage.accountExpired.checked = '1'
        browser.$('input', type: 'radio', name: 'accountExpired', value: '1').click()
        userSearchPage.submit()

        then:
        browser.at(UserSearchPage)
        userSearchPage.assertResults(1, 3, 3)
        assertContentContains('maryrose')
        assertContentContains('ratuig')
        assertContentContains('rome20c')
    }

    void testFindByAccountLocked() {
        when:
        def userSearchPage = browser.to(UserSearchPage)

        // Temporary workaround for problem with Geb RadioButtons module
        //userSearchPage.accountLocked.checked = '1'
        browser.$('input', type: 'radio', name: 'accountLocked', value: '1').click()
        userSearchPage.submit()

        then:
        browser.at(UserSearchPage)
        userSearchPage.assertResults(1, 3, 3)
        assertContentContains('aaaaaasd')
        assertContentContains('achen')
        assertContentContains('szhang1999')
    }

    void testFindByPasswordExpired() {
        when:
        def userSearchPage = browser.to(UserSearchPage)

        // Temporary workaround for problem with Geb RadioButtons module
        //userSearchPage.passwordExpired.checked = '1'
        browser.$('input', type: 'radio', name: 'passwordExpired', value: '1').click()
        userSearchPage.submit()

        then:
        browser.at(UserSearchPage)
        userSearchPage.assertResults(1, 3, 3)
        assertContentContains('hhheeeaaatt')
        assertContentContains('mscanio')
        assertContentContains('kittal')
    }

    void testCreateAndEdit() {
        given:
        String newUsername = "newuser${UUID.randomUUID()}"

        // make sure it doesn't exist
        when:
        def userSearchPage = browser.to(UserSearchPage).tap {
            username = newUsername
            submit()
        }

        then:
        browser.at(UserSearchPage)
        userSearchPage.assertNoResults()

        // create
        when:
        browser.to(UserCreatePage).with {
            username = newUsername
            password =  'password'
            enabled.check()
            submit()
        }

        then:
        def userEditPage = browser.at(UserEditPage)
        userEditPage.username.text == newUsername
        userEditPage.enabled.checked
        !userEditPage.accountExpired.checked
        !userEditPage.accountLocked.checked
        !userEditPage.passwordExpired.checked

        // edit
        when:
        String updatedName = "${newUsername}_updated"
        userEditPage.with {
            username = updatedName
            enabled.uncheck()
            accountExpired.check()
            accountLocked.check()
            passwordExpired.check()
            submit()
        }

        then:
        browser.at(UserEditPage)
        userEditPage.username.text == updatedName
        !userEditPage.enabled.checked
        userEditPage.accountExpired.checked
        userEditPage.accountLocked.checked
        userEditPage.passwordExpired.checked

        // delete
        when:
        userEditPage.delete()

        then:
        browser.at(UserSearchPage)

        when:
        userSearchPage.with {
            username = updatedName
            submit()
        }

        then:
        browser.at(UserSearchPage)
        userSearchPage.assertNoResults()
    }

    @Issue('https://github.com/grails-plugins/grails-spring-security-ui/issues/89')
    void testUserRoleAssociationsAreNotRemoved() {
        when: 'edit user 1'
        browser.go('user/edit/1')

        then:
        def userEditPage = browser.at(UserEditPage)

        when: 'select Roles tab'
        userEditPage.rolesTab.select()

        then: '12 roles are listed and 1 is enabled'
        userEditPage.rolesTab.totalRoles() == 12
        userEditPage.rolesTab.totalEnabledRoles() == 1
        userEditPage.rolesTab.hasEnabledRole('ROLE_USER')

        when: 'ROLE_ADMIN is enabled and the changes are saved'
        userEditPage.with {
            rolesTab.enableRole 'ROLE_ADMIN'
            submit()
            rolesTab.select()
        }

        then: '12 roles are listed and 2 are enabled'
        userEditPage.rolesTab.totalEnabledRoles() == 2
        userEditPage.rolesTab.hasEnabledRoles(['ROLE_USER', 'ROLE_ADMIN'])
        userEditPage.rolesTab.totalRoles() == 12
    }

    @Issue('https://github.com/grails-plugins/grails-spring-security-ui/issues/106')
    void testUserRoleAssociationsAreRemoved() {
        when: 'edit user 2'
        browser.go('user/edit/2')

        then:
        def userEditPage = browser.at(UserEditPage)

        when: 'select Roles tab'
        userEditPage.rolesTab.select()

        then: '12 roles are listed and 1 is enabled'
        userEditPage.rolesTab.totalRoles() == 12
        userEditPage.rolesTab.totalEnabledRoles() == 1
        userEditPage.rolesTab.hasEnabledRole('ROLE_USER')

        when: 'ROLE_ADMIN is enabled and the changes are saved'
        userEditPage.with {
            rolesTab.enableRole('ROLE_ADMIN')
            submit()
            rolesTab.select()
        }

        then: '12 roles are listed and 2 are enabled'
        userEditPage.rolesTab.totalEnabledRoles() == 2
        userEditPage.rolesTab.hasEnabledRoles(['ROLE_USER', 'ROLE_ADMIN'])
        userEditPage.rolesTab.totalRoles() == 12

        when: 'edit user 2'
        browser.go('user/edit/2')

        then:
        browser.at(UserEditPage)

        when: 'select Roles tab'
        userEditPage.rolesTab.select()

        then: '12 roles are listed and 2 are enabled'
        userEditPage.rolesTab.totalRoles() == 12
        userEditPage.rolesTab.totalEnabledRoles() == 2
        userEditPage.rolesTab.hasEnabledRole('ROLE_USER')

        when: 'ROLE_ADMIN is disabled and the changes are saved'
        userEditPage.with {
            rolesTab.disableRole('ROLE_ADMIN')
            submit()
        }
        browser.go('user/edit/2')

        then:
        browser.at(UserEditPage)

        when: 'select Roles tab'
        userEditPage.rolesTab.select()

        then: '12 roles are listed and 1 is enabled'
        userEditPage.rolesTab.totalEnabledRoles() == 1
        userEditPage.rolesTab.hasEnabledRoles(['ROLE_USER'])
        userEditPage.rolesTab.totalRoles() == 12
    }
}
