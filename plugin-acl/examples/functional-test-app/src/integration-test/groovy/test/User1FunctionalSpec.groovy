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

	// user1 has admin on 11,12 and read on 1-67

	void setup() {
		login 'user1'
	}

	void 'check tags'() {
		when:
		go 'tagLibTest/test'

		then:
		assertContentContains 'test 1 true 1'
		assertContentContains 'test 2 true 1'
		assertContentContains 'test 3 true 1'
		assertContentContains 'test 4 true 1'
		assertContentContains 'test 5 true 1'
		assertContentContains 'test 6 true 1'

		assertContentContains 'test 1 true 13'
		assertContentContains 'test 2 true 13'
		assertContentContains 'test 3 true 13'
		assertContentContains 'test 4 true 13'
		assertContentContains 'test 5 true 13'
		assertContentContains 'test 6 true 13'

		assertContentContains 'test 1 false 80'
		assertContentContains 'test 2 false 80'
		assertContentContains 'test 3 false 80'
		assertContentContains 'test 4 false 80'
		assertContentContains 'test 5 false 80'
		assertContentContains 'test 6 false 80'
	}

	void 'view all (1-67)'() {
		when:
		go "report/show?number=$i"

		then:
		assertContentContains "report$i"

		where:
		i << (1..67)
	}

	void 'view all (68-100)'() {
		when:
		go "report/show?number=$i"

		then:
		assertContentContains 'Access Denied'

		where:
		i << (68..100)
	}

	void 'edit report 11'() {
		when:
		go 'report/edit?number=11'

		then:
		at EditReportPage
		$('form').name == 'report11'

		when:
		name = 'report11_new'
		updateButton.click()

		then:
		at ShowReportPage
		assertContentContains 'report11_new'
	}

	void 'delete report 11'() {
		when:
		go 'report/delete?number=11'

		then:
		at ListReportPage
		message == "Report 11 deleted"
		reportRows.size() == 66
	}

	void 'grant edit 12'() {
		when:
		go 'report/grant?number=12'

		then:
		at ReportGrantPage
		assertContentContains 'Grant permission for report12'

		when:
		recipient = 'user2'
		permission = BasePermission.READ.mask.toString()
		grantButton.click()

		then:
		at ShowReportPage
		assertContentContains "Permission $BasePermission.READ.mask granted on Report 12 to user2"

		when:
		go 'report/grant?number=12'

		then:
		at ReportGrantPage
		assertContentContains 'Grant permission for report12'

		when:
		recipient = 'user2'
		permission = BasePermission.WRITE.mask.toString()
		grantButton.click()

		then:
		at ShowReportPage
		assertContentContains "Permission $BasePermission.WRITE.mask granted on Report 12 to user2"
	}

	void 'grant edit 13'() {
		when:
		go 'report/grant?number=13'

		then:
		at ReportGrantPage
		assertContentContains 'Grant permission for report13'

		when:
		recipient = 'user2'
		permission = BasePermission.WRITE.mask.toString()
		grantButton.click()

		then:
		assertContentContains 'Access Denied'
	}

	void 'edit report 20'() {
		when:
		go 'report/edit?number=20'

		then:
		at EditReportPage
		$('form').name == 'report20'

		when:
		name = 'report20_new'
		updateButton.click()

		then:
		assertContentContains 'Access Denied'
	}
}
