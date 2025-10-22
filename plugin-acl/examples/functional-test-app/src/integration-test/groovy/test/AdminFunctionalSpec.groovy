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

import grails.testing.mixin.integration.Integration
import org.springframework.security.acls.domain.BasePermission

import pages.EditReportPage
import pages.IndexPage
import pages.ListReportPage
import pages.ReportGrantPage
import pages.ShowReportPage
import spock.lang.Stepwise

@Stepwise
@Integration
class AdminFunctionalSpec extends AbstractSecuritySpec {

	// admin has admin on all

	void setup() {
		login('admin')
	}

	void 'check tags'() {
		when:
		go('tagLibTest/test')

		then:
		pageSource.contains('test 1 true 1')
		pageSource.contains('test 2 true 1')
		pageSource.contains('test 3 true 1')
		pageSource.contains('test 4 true 1')
		pageSource.contains('test 5 true 1')
		pageSource.contains('test 6 true 1')

		pageSource.contains('test 1 true 13')
		pageSource.contains('test 2 true 13')
		pageSource.contains('test 3 true 13')
		pageSource.contains('test 4 true 13')
		pageSource.contains('test 5 true 13')
		pageSource.contains('test 6 true 13')

		pageSource.contains('test 1 true 80')
		pageSource.contains('test 2 true 80')
		pageSource.contains('test 3 true 80')
		pageSource.contains('test 4 true 80')
		pageSource.contains('test 5 true 80')
		pageSource.contains('test 6 true 80')
	}

	void 'view all'() {
		when:
		go("report/show?number=$i")

		then:
		waitFor { pageSource.contains("report$i") }

		where:
		i << (1..100)
	}

	void 'edit report 15'() {

		when:
		go('report/edit?number=15')

		then:
		at(EditReportPage)
		$('form').name == 'report15'

		when:
		name = 'report15_new'
		updateButton.click()

		then:
		at(ShowReportPage)
		pageSource.contains('report15_new')
	}

	void 'delete report 15'() {
		when:
		go('report/delete?number=15')
		def listReportPage = at(ListReportPage)

		then:
		message == 'Report 15 deleted'
		listReportPage.reportRows.size() == 99
	}

	void 'grant edit 16'() {
		when:
		go('report/grant?number=16')
		def reportGrantPage = at(ReportGrantPage)

		then:
		pageSource.contains('Grant permission for report16')

		when:
		recipient = 'user2'
		permission = BasePermission.READ.mask.toString()
		reportGrantPage.grantButton.click()
		at(ShowReportPage)

		then:
		pageSource.contains("Permission $BasePermission.READ.mask granted on Report 16 to user2")

		// login as user2 and verify the grant
		when:
		go('logout')

		then:
		at(IndexPage)

		when:
		login('user2')

		then:
		at(IndexPage)

		when:
		go('report/show?number=16')

		then:
		pageSource.contains('report16')
	}
}
