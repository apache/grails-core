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

import com.testapp.TestDataService
import spock.lang.Stepwise

import grails.testing.mixin.integration.Integration
import pages.requestmap.CreateRequestmapPage
import pages.requestmap.EditRequestmapPage
import pages.requestmap.ListRequestmapPage
import pages.requestmap.ShowRequestmapPage
import spock.lang.IgnoreIf

@Stepwise
@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'requestmap' })
class RequestmapSpec extends AbstractSecuritySpec {

    void 'test request maps are initially present'() {
        when:
        def page = to(ListRequestmapPage, max: 100)

        then:
        page.requestmapRows.size() == TestDataService.URIS_FOR_REQUESTMAPS.size()
    }

    void 'add a requestmap'() {
        when:
        to(ListRequestmapPage).with {
            newRequestmapButton.click()
        }

        and:
        def page = at(CreateRequestmapPage)
        page.urlField.text = '/nuevo/**'
        page.configAttributeField.text = 'ROLE_ADMIN'
        page.createButton.click()
        page = at(ShowRequestmapPage)

        then:
        page.value('URL') == '/nuevo/**'
        page.configAttribute == 'ROLE_ADMIN'

        when:
        page = to(ListRequestmapPage, max: 100)

        then:
        page.requestmapRows.size() == (TestDataService.URIS_FOR_REQUESTMAPS.size() + 1)
    }

    void 'edit the details'() {
        when:
        def page = to(ListRequestmapPage, max: 100)
        page.requestmapRow(19).showLink.click()
        page = at(ShowRequestmapPage)

        and:
        page.editButton.click()
        page = at(EditRequestmapPage)

        and:
        page.urlField.text = '/secure2/**'
        page.configAttributeField.text = 'ROLE_ADMINX'
        page.updateButton.click()
        page = at(ShowRequestmapPage)

        then:
        page.value('URL') == '/secure2/**'
        page.configAttribute == 'ROLE_ADMINX'
    }

    void 'delete requestmap'() {
        when:
        def page = to(ListRequestmapPage, max: 100)
        page.requestmapRow(19).showLink.click()
        page = at(ShowRequestmapPage)
        def deletedId = page.id

        and:
        withConfirm { page.deleteButton.click() }
        page = at(ListRequestmapPage)

        then:
        page.message == "TestRequestmap $deletedId deleted"

        when:
        page = to(ListRequestmapPage, max: 100)

        then:
        page.requestmapRows.size() == TestDataService.URIS_FOR_REQUESTMAPS.size()
    }
}
