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
package specs

import pages.user.CreateUserPage
import pages.user.EditUserPage
import pages.user.ListUserPage
import pages.user.ShowUserPage
import spock.lang.IgnoreIf
import spock.lang.Stepwise

import grails.testing.mixin.integration.Integration

@Stepwise
@Integration
@IgnoreIf({ !(
        System.getProperty('TESTCONFIG') == 'annotation' ||
        System.getProperty('TESTCONFIG') == 'basic' ||
        System.getProperty('TESTCONFIG') == 'basicCacheUsers' ||
        System.getProperty('TESTCONFIG') == 'requestmap' ||
        System.getProperty('TESTCONFIG') == 'static')
})
class UserSpec extends AbstractSecuritySpec {

    void 'there are no users initially'() {
        when:
        def page = to(ListUserPage)

        then:
        page.userRows.size() == 0
    }

    void 'add a user'() {
        when:
        def page = to(ListUserPage)
        page.newUserButton.click()

        and:
        page = at(CreateUserPage)
        page.usernameField.text = 'new_user'
        page.passwordField.text = 'p4ssw0rd'
        page.enabledCheckbox.check()
        page.createButton.click()
        page = at(ShowUserPage)

        then:
        page.username == 'new_user'
        page.userEnabled == true
    }

    void 'edit the details'() {
        when:
        def page = to(ListUserPage)
        page.userRow(0).showLink.click()

        and:
        page = at(ShowUserPage)
        page.editButton.click()
        page = at(EditUserPage)

        and:
        page.usernameField.text = 'new_user2'
        page.passwordField.text = 'p4ssw0rd2'
        page.enabledCheckbox.uncheck()
        page.updateButton.click()

        then:
        at(ShowUserPage)

        when:
        page = to(ListUserPage)

        then:
        page.userRows.size() == 1
        def row = page.userRow(0)
        row.username == 'new_user2'
        !row.userEnabled
    }

    void 'show user'() {
        when:
        def page = to(ListUserPage)
        page.userRow(0).showLink.click()

        then:
        at(ShowUserPage)
    }

    void 'delete user'() {
        when:
        def page = to(ListUserPage)
        page.userRow(0).showLink.click()
        def deletedId = page.id
        page = at(ShowUserPage)

        and:
        withConfirm { page.deleteButton.click() }
        page = at(ListUserPage)

        then:
        page.message == "TestUser $deletedId deleted."
        page.userRows.size() == 0
    }
}
