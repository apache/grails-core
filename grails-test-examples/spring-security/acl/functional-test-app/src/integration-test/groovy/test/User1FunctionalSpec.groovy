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

import static org.springframework.security.acls.domain.BasePermission.READ
import static org.springframework.security.acls.domain.BasePermission.WRITE

@Integration
class User1FunctionalSpec extends AbstractSecuritySpec {

	// user1 has admin on 11-12 and read on 1-67

	void setup() {
		login('user1')
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

			contains('test 1 false 80')
			contains('test 2 false 80')
			contains('test 3 false 80')
			contains('test 4 false 80')
			contains('test 5 false 80')
			contains('test 6 false 80')
		}
	}

	@Unroll
	void 'view all (1-67)'() {
		when:
		def page = to(ShowReportPage, i)

		then:
		page.name == "report$i"

		where:
		i << (1..67)
	}

	@Unroll
	void 'view all (68-100)'() {
		when:
		via(ShowReportPage, i)

		then:
		at(AccessDeniedPage)

		where:
		i << (68..69)
	}

	void 'edit report 11'() {
		when:
		def page = to(EditReportPage, 11)

		then:
		page.nameField.text == 'report11'

		when:
		page.nameField.text = 'report11_new'
		page.updateButton.click()
		page = at(ShowReportPage)

		then:
		page.name == 'report11_new'
	}

	void 'delete report 11'() {
		when:
		via(DeleteReportPage, 11)
		def page = at(ListReportPage)

		then:
		page.message == 'Report 11 deleted'
		page.reportRows.size() == 66
	}

	void 'grant edit 12'() {
		when:
		def page = to(ReportGrantPage, 12)

		then:
		page.heading == 'Grant permission for report12'

		when:
		page.grantPermission('user2', READ)
		page = at(ShowReportPage)

		then:
		page.message == "Permission $READ.mask granted on Report 12 to user2"

		when:
		page = to(ReportGrantPage, 12)

		then:
		page.heading == 'Grant permission for report12'

		when:
		page.grantPermission('user2', WRITE)

		then:
		at(ShowReportPage)
		page.message == "Permission $WRITE.mask granted on Report 12 to user2"
	}

	void 'grant edit 13'() {
		when:
		def page = to(ReportGrantPage, 13)

		then:
		page.heading == 'Grant permission for report13'

		when:
		page.grantPermission('user2', WRITE)

		then:
		at(AccessDeniedPage)
	}

	void 'edit report 20'() {
		when:
		def page = to(EditReportPage, 20)

		then:
		page.nameField.text == 'report20'

		when:
		page.nameField.text = 'report20_new'
		page.updateButton.click()

		then:
		at(AccessDeniedPage)
	}
}
