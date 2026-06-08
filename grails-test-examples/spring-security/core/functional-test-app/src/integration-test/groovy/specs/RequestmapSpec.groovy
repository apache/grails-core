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
import grails.testing.mixin.integration.Integration
import pages.requestmap.CreateRequestmapPage
import pages.requestmap.EditRequestmapPage
import pages.requestmap.ListRequestmapPage
import pages.requestmap.ShowRequestmapPage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'requestmap' })
class RequestmapSpec extends AbstractSecuritySpec {

	void 'test request maps are initially present'() {
		when:
		go 'testRequestmap/list?max=100'

		then:
		at ListRequestmapPage
		requestmapRows.size() == TestDataService.URIS_FOR_REQUESTMAPS.size()
	}

	void 'add a requestmap'() {
		when:
		to ListRequestmapPage
		newRequestmapButton.click()

		then:
		at CreateRequestmapPage

		when:
		$('form').url = '/nuevo/**'
		configAttribute = 'ROLE_ADMIN'
		createButton.click()

		then:
		at ShowRequestmapPage
		value('URL') == '/nuevo/**'
		configAttribute == 'ROLE_ADMIN'

		when:
		go 'testRequestmap/list?max=100'

		then:
		at ListRequestmapPage
		requestmapRows.size() == (TestDataService.URIS_FOR_REQUESTMAPS.size() + 1)
	}

	void 'edit the details'() {
		when:
		go 'testRequestmap/list?max=100'

		then:
		at ListRequestmapPage

		when:
		requestmapRow(19).showLink.click()

		then:
		at ShowRequestmapPage

		when:
		editButton.click()

		then:
		at EditRequestmapPage

		when:
		$('form').url = '/secure2/**'
		configAttribute = 'ROLE_ADMINX'
		updateButton.click()

		then:
		at ShowRequestmapPage
		value('URL') == '/secure2/**'
		configAttribute == 'ROLE_ADMINX'
	}

	void 'delete requestmap'() {
		when:
		go 'testRequestmap/list?max=100'

		then:
		at ListRequestmapPage

		when:
		requestmapRow(19).showLink.click()

		then:
		at ShowRequestmapPage

		when:
		def deletedId = id
		withConfirm { deleteButton.click() }

		then:
		at ListRequestmapPage
		message == "TestRequestmap $deletedId deleted"

		when:
		go 'testRequestmap/list?max=100'

		then:
		requestmapRows.size() == TestDataService.URIS_FOR_REQUESTMAPS.size()
	}
}
