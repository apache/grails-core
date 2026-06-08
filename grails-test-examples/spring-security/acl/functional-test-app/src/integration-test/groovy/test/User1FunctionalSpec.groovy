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
import pages.ListReportPage
import pages.ReportGrantPage
import pages.ShowReportPage
import spock.lang.Stepwise

@Stepwise
@Integration
class User1FunctionalSpec extends AbstractSecuritySpec {

	// user1 has admin on 11-12 and read on 1-67

	void setup() {
		login('user1')
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

		pageSource.contains('test 1 false 80')
		pageSource.contains('test 2 false 80')
		pageSource.contains('test 3 false 80')
		pageSource.contains('test 4 false 80')
		pageSource.contains('test 5 false 80')
		pageSource.contains('test 6 false 80')
	}

	void 'view all (1-67)'() {
		when:
		go("report/show?number=$i")

		then:
		pageSource.contains("report$i")

		where:
		i << (1..67)
	}

	void 'view all (68-100)'() {
		when:
		go("report/show?number=$i")

		then:
		pageSource.contains('Access Denied')

		where:
		i << (68..100)
	}

	void 'edit report 11'() {
		when:
		go('report/edit?number=11')
		def editPage = at(EditReportPage)

		then:
		$('form').name == 'report11'

		when:
		name = 'report11_new'
		editPage.updateButton.click()

		then:
		at(ShowReportPage)
		pageSource.contains('report11_new')
	}

	void 'delete report 11'() {
		when:
		go('report/delete?number=11')
		def listPage = at(ListReportPage)

		then:
		message == 'Report 11 deleted'
		listPage.reportRows.size() == 66
	}

	void 'grant edit 12'() {
		when:
		go('report/grant?number=12')
		def grantPage = at(ReportGrantPage)

		then:
		pageSource.contains('Grant permission for report12')

		when:
		recipient = 'user2'
		permission = BasePermission.READ.mask.toString()
		grantPage.grantButton.click()

		then:
		at(ShowReportPage)
		pageSource.contains("Permission $BasePermission.READ.mask granted on Report 12 to user2")

		when:
		go('report/grant?number=12')
		grantPage = at(ReportGrantPage)

		then:
		pageSource.contains('Grant permission for report12')

		when:
		recipient = 'user2'
		permission = BasePermission.WRITE.mask.toString()
		grantPage.grantButton.click()

		then:
		at(ShowReportPage)
		pageSource.contains("Permission $BasePermission.WRITE.mask granted on Report 12 to user2")
	}

	void 'grant edit 13'() {
		when:
		go('report/grant?number=13')
		def grantPage = at(ReportGrantPage)

		then:
		pageSource.contains('Grant permission for report13')

		when:
		recipient = 'user2'
		permission = BasePermission.WRITE.mask.toString()
		grantPage.grantButton.click()

		then:
		pageSource.contains('Access Denied')
	}

	void 'edit report 20'() {
		when:
		go('report/edit?number=20')
		def editPage = at(EditReportPage)

		then:
		$('form').name == 'report20'

		when:
		name = 'report20_new'
		editPage.updateButton.click()

		then:
		pageSource.contains('Access Denied')
	}
}
