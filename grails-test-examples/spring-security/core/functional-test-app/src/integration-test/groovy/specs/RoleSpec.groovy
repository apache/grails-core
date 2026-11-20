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

import pages.role.CreateRolePage
import pages.role.EditRolePage
import pages.role.ListRolePage
import pages.role.ShowRolePage
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
class RoleSpec extends AbstractSecuritySpec {

    void 'there are no roles initially'() {
        when:
        def page = to(ListRolePage)

        then:
        page.roleRows.size() == 0
    }

    void 'add a role'() {
        when:
        def page = to(ListRolePage)
        page.newRoleButton.click()

        and:
        page = at(CreateRolePage)
        page.authorityField.text = 'test'
        page.createButton.click()

        and:
        page = at(ShowRolePage)

        then:
        page.authority == 'test'
    }

    void 'edit the details'() {
        when:
        def page = to(ListRolePage)
        page.roleRow(0).showLink.click()
        page = at(ShowRolePage)

        and:
        page.editButton.click()
        page = at(EditRolePage)

        and:
        page.authorityField.text = 'test_new'
        page.updateButton.click()

        then:
        at(ShowRolePage)

        when:
        page = to(ListRolePage)

        then:
        //page.roleRows.size() == 1
        page.roleRow(0).authority == 'test_new'
    }

    void 'show role'() {
        when:
        def page = to(ListRolePage)
        page.roleRow(0).showLink.click()

        then:
        at(ShowRolePage)
    }

    void 'delete role'() {
        when:
        def page = to(ListRolePage).tap {
            roleRow(0).showLink.click()
        }
        def deletedId = page.id

        and:
        page = at(ShowRolePage)

        and:
        withConfirm { page.deleteButton.click() }

        and:
        page = at(ListRolePage)

        then:
        page.message == "TestRole $deletedId deleted"
        page.roleRows.size() == 0
    }
}
