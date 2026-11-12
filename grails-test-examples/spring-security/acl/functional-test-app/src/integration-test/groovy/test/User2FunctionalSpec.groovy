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

import pages.AccessDeniedPage
import pages.DeleteReportPage
import pages.EditReportPage
import pages.ListReportPage
import pages.ReportGrantPage
import pages.ShowReportPage
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration

import static org.springframework.security.acls.domain.BasePermission.WRITE

@Integration
class User2FunctionalSpec extends AbstractSecuritySpec {

	// user2 has read on 1-5, write on 5

	void setup() {
		login('user2')
	}

	@Unroll
	void 'view all (1-5)'() {
		when:
		def page = to(ShowReportPage, 1)

		then:
		page.name == 'report1'

		where:
		i << (1..5)
	}

	@Unroll
	void 'view all (6-100)'() {
		when:
		via(ShowReportPage, i)

		then:
		at(AccessDeniedPage)

		where:
		i << (6..100)
	}

	void 'edit report 11'() {
		when:
		via(EditReportPage, 11)

		then:
		at(AccessDeniedPage)
	}

	void 'delete report 1'() {
		when:
		via(DeleteReportPage, 1)

		then:
		at(AccessDeniedPage)
	}

	void 'grant edit 2'() {
		when:
		def page = to(ReportGrantPage, 2)

		then:
		page.heading == 'Grant permission for report2'

		when:
		page.grantPermission('user1', WRITE)

		then:
		at(AccessDeniedPage)
	}

	void 'edit report 5'() {
		when:
		def page = to(EditReportPage, 5)

		then:
		page.nameField.text == 'report5'

		when:
		page.nameField.text = 'report5_new'
		page.updateButton.click()
		page = at(ShowReportPage)

		then:
		page.name == 'report5_new'
	}

	void 'list is filtered'() {
		when:
		def page = to(ListReportPage)

		then:
		page.reportRows[0].name == 'report1'
		page.reportRows.every {it.name != 'report6' }

		when:
		to(ListReportPage, [offset: 80, max: 10])

		then:
		page.nextLink.displayed
		page.reportRows.every {it.name != 'report85' }
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

			contains('test 1 false 13')
			contains('test 2 false 13')
			contains('test 3 false 13')
			contains('test 4 false 13')
			contains('test 5 false 13')
			contains('test 6 false 13')

			contains('test 1 false 80')
			contains('test 2 false 80')
			contains('test 3 false 80')
			contains('test 4 false 80')
			contains('test 5 false 80')
			contains('test 6 false 80')
		}
	}
}
