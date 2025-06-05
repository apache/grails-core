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

import grails.gorm.transactions.Rollback
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import test.Report
import test.TestService
import grails.testing.mixin.integration.Integration


/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Integration
@Rollback
abstract class AbstractAclMappedServiceSpec extends AbstractAclSpec {

	protected TestService service

	void 'getReport() fails when not authenticated'() {
        given:
        buildReports()

		when:
		Report report = Report.get(report1Id)

		then:
		report

		when:
		service.getReport report1Id

		then:
		thrown AuthenticationCredentialsNotFoundException
	}

	void 'getReport() fails when authenticated if the user has no grants for the instance'() {
		given:
        buildReports()
		authenticateAsUser()

		when:
		service.getReport report1Id

		then:
		def e = thrown(AccessDeniedException)
		e.message.startsWith "Authentication user has NO permissions to the domain object Report $report1Id r1"
	}

	void 'getReport() succeeds when authenticated if the user has grants for the instance'() {
		given:
        buildReports()
		authenticateAsAdmin()
		aclUtilService.addPermission Report, report1Id, USER, BasePermission.READ
		authenticateAsUser()

		when:
		Report report = service.getReport(report1Id)

		then:
		report
		report1Id == report.id
	}

	void 'getAllReports() succeeds when authenticated if the user has appropriate grants'() {
		given:
        buildReports()
		authenticateAsAdmin()
		aclUtilService.addPermission Report, report1Id, USER, BasePermission.READ
		authenticateAsUser()

		when:
		List<Report> reports = service.getAllReports()

		then:
		1 == reports.size()
		report1Id == reports[0].id
	}

	void 'updateReport() succeeds when authenticated if the user has appropriate grants'() {
        given:
        buildReports()

		when:
		String newName = 'new_name'
		Report report = Report.get(report1Id)

		then:
		report.name != newName

		when:
		service.updateReport(report, [name: newName])

		then:
		// not logged in
		thrown AuthenticationCredentialsNotFoundException

		when:
		authenticateAsAdmin()
		aclUtilService.addPermission Report, report1Id, USER, BasePermission.READ

		authenticateAsUser()
		service.updateReport(report, [name: newName])

		then:
		// no grant for write
		thrown AccessDeniedException

		when:
		authenticateAsAdmin()
		aclUtilService.addPermission Report, report1Id, USER, BasePermission.WRITE

		authenticateAsUser()

		service.updateReport(report, [name: newName])
		report = Report.get(report1Id)

		then:
		newName == report.name
	}
}
