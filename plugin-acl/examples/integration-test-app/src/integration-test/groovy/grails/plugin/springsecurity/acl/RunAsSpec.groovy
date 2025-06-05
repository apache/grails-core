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
package grails.plugin.springsecurity.acl

import org.springframework.security.access.AccessDeniedException
import test.TestRunAsService
import test.TestSecureService
import grails.testing.mixin.integration.Integration


/**
 * Integration tests for run-as functionality.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Integration
@grails.gorm.transactions.Rollback
class RunAsSpec extends AbstractIntegrationSpec {

	TestRunAsService testRunAsService
	TestSecureService testSecureService

	void 'not authenticated'() {

		given:
		authenticate 'ROLE_ANONYMOUS'

		when:
		testRunAsService.method1()

		then:
		thrown AccessDeniedException

		when:
		testRunAsService.method2()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method1()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method2()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method3()

		then:
		thrown AccessDeniedException
	}

	void 'authenticated admin'() {

		given:
		authenticateAsAdmin()

		when:
		testSecureService.method1()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method2()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method3()

		then:
		thrown AccessDeniedException

		'method1' == testRunAsService.method1()
		'method2' == testRunAsService.method2()
	}

	void 'authenticated user'() {

		given:
		authenticateAsUser()

		when:
		testRunAsService.method1()

		then:
		thrown AccessDeniedException

		when:
		testRunAsService.method2()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method1()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method2()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method3()

		then:
		thrown AccessDeniedException
	}

	void 'authenticated superuser'() {

		given:
		authenticate 'ROLE_SUPERUSER'

		when:
		testRunAsService.method1()

		then:
		thrown AccessDeniedException

		when:
		testRunAsService.method2()

		then:
		thrown AccessDeniedException

		when:
		testSecureService.method1()

		then:
		thrown AccessDeniedException

		'method2' == testSecureService.method2()
		'method3' == testSecureService.method3()
	}
}
