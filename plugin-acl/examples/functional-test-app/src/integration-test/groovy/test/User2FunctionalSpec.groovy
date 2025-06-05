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
import pages.ReportGrantPage
import pages.ShowReportPage
import spock.lang.Stepwise

@Stepwise
@Integration
class User2FunctionalSpec extends AbstractSecuritySpec {

	// user2 has read on 1-5, write on 5

	void setup() {
		login 'user2'
	}

	void 'view all (1-5)'() {
		when:
		go "report/show?number=$i"

		then:
		assertContentContains "report$i"

		where:
		i << (1..5)
	}

	void 'view all (6-100)'() {
		when:
		go "report/show?number=$i"

		then:
		assertContentContains 'Access Denied'

		where:
		i << (6..100)
	}

	void 'edit report 11'() {

		when:
		go 'report/edit?number=11'

		then:
		assertContentContains 'Access Denied'
	}

	void 'delete report 1'() {
		when:
		go 'report/delete?number=1'

		then:
		assertContentContains 'Access Denied'
	}

	void 'grant edit 2'() {
		when:
		go 'report/grant?number=2'

		then:
		at ReportGrantPage
		assertContentContains 'Grant permission for report2'

		when:
		recipient = 'user1'
		permission = BasePermission.WRITE.mask.toString()
		grantButton.click()

		then:
		assertContentContains 'Access Denied'
	}

	void 'edit report 5'() {
		when:
		go 'report/edit?number=5'

		then:
		at EditReportPage
		$('form').name == 'report5'

		when:
		name = 'report5_new'
		updateButton.click()

		then:
		at ShowReportPage
		assertContentContains 'report5_new'
	}

	void 'list is filtered'() {

		when:
		go 'report/list'

		then:
		assertContentContains 'report5'
		assertContentDoesNotContain 'report6'

		when:
		go 'report/list?offset=80&max=10'

		then:
		assertContentContains 'Next'
		assertContentDoesNotContain 'report85'
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

		assertContentContains 'test 1 false 13'
		assertContentContains 'test 2 false 13'
		assertContentContains 'test 3 false 13'
		assertContentContains 'test 4 false 13'
		assertContentContains 'test 5 false 13'
		assertContentContains 'test 6 false 13'

		assertContentContains 'test 1 false 80'
		assertContentContains 'test 2 false 80'
		assertContentContains 'test 3 false 80'
		assertContentContains 'test 4 false 80'
		assertContentContains 'test 5 false 80'
		assertContentContains 'test 6 false 80'
	}
}
