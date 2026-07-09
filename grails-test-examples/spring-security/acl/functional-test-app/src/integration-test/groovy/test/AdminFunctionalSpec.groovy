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

import pages.DeleteReportPage
import pages.EditReportPage
import pages.IndexPage
import pages.ListReportPage
import pages.ReportGrantPage
import pages.ShowReportPage
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration

import static org.springframework.security.acls.domain.BasePermission.READ

@Integration
class AdminFunctionalSpec extends AbstractSecuritySpec {

	// admin has admin on all

	void setup() {
		login('admin')
	}

	void 'check tags'() {
		when:
		go('/tagLibTest/test')

		then:
		with(pageSource) {
			contains('test 1 true 1')
			contains('test 2 true 1')
			contains('test 3 true 1')
			contains('test 4 true 1')
			contains('test 5 true 1')
			contains('test 6 true 1')

			contains('test 1 true 13')
			contains('test 2 true 13')
			contains('test 3 true 13')
			contains('test 4 true 13')
			contains('test 5 true 13')
			contains('test 6 true 13')

			contains('test 1 true 80')
			contains('test 2 true 80')
			contains('test 3 true 80')
			contains('test 4 true 80')
			contains('test 5 true 80')
			contains('test 6 true 80')
		}
	}

	@Unroll
	void 'view all'() {
		when:
		def page = to(ShowReportPage, i)

		then:
		page.name == "report$i"

		where:
		i << (1..100)
	}

	void 'edit report 15'() {
		when:
		def page = to(EditReportPage, 15)

		then:
		page.nameField.text == 'report15'

		when:
		page.nameField = 'report15_new'
		page.updateButton.click()

		then:
		at(ShowReportPage)
		pageSource.contains('report15_new')
	}

	void 'delete report 15'() {
		when:
		via(DeleteReportPage, 15)

		then:
		def page = at(ListReportPage)

		and:
		page.message == 'Report 15 deleted'
		page.reportRows.size() == 99
	}

	void 'grant edit 16'() {
		when:
		def page = to(ReportGrantPage, 16)

		then:
		page.heading == 'Grant permission for report16'

		when:
		page.grantPermission('user2', READ)
		page = at(ShowReportPage)

		then:
		page.message == "Permission $READ.mask granted on Report 16 to user2"

		// login as user2 and verify the grant
		when:
		logout()

		then:
		at(IndexPage)

		when:
		login('user2')

		and:
		page = to(ShowReportPage, 16)

		then:
		page.name == 'report16'
	}
}
