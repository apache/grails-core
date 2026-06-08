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
import grails.plugin.springsecurity.acl.util.ProxyUtils
import org.springframework.security.acls.domain.CumulativePermission
import spock.lang.Issue
import test.Report

import static org.springframework.security.acls.domain.BasePermission.READ
import static org.springframework.security.acls.domain.BasePermission.WRITE
import grails.testing.mixin.integration.Integration

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Integration
@Rollback
class AclUtilServiceSpec extends AbstractAclSpec {

	void 'permissions let you do things'() {
		given:
		buildReports()

		when:
		authenticateAsAdmin()

		then:
		0 == AclClass.count()
		0 == AclEntry.count()
		0 == AclObjectIdentity.count()
		0 == AclSid.count()

		when:
		aclUtilService.addPermission Report.get(report1Id), USER, READ

		then:
		1 == AclClass.count()

		when:
		def aclClass = AclClass.list()[0]

		then:
		Report.name == aclClass.className

		2 == AclSid.count()

		when:
		def adminSid = AclSid.list()[0]
		def userSid = AclSid.list()[1]

		then:
		ADMIN == adminSid.sid
		USER == userSid.sid

		1 == AclObjectIdentity.count()

		when:
		def identity = AclObjectIdentity.list()[0]

		then:
		aclClass == identity.aclClass
		report1Id == identity.objectId

		1 == AclEntry.count()

		when:
		def entry = AclEntry.list()[0]

		then:
		userSid == entry.sid
		identity == entry.aclObjectIdentity
		0 == entry.aceOrder
		entry.granting
		1 == entry.mask
	}

	void 'has permission'() {
		given:
		buildReports()
		def report = Report.get(report1Id)
		authenticateAsAdmin()

		def userAuth = authenticateAsUser(false)

		when:
		aclUtilService.addPermission(report, USER, READ)

		then:
		aclUtilService.hasPermission(userAuth, report, READ)
		!aclUtilService.hasPermission(userAuth, report, WRITE)
		!aclUtilService.hasPermission(userAuth, Report.get(report2Id), READ)

		when:
		aclUtilService.addPermission(report, USER, WRITE)

		then:
		aclUtilService.hasPermission(userAuth, report, READ)
		aclUtilService.hasPermission(userAuth, report, WRITE)
		!aclUtilService.hasPermission(userAuth, Report.get(report2Id), READ)
	}

	void 'delete permission'() {
		given:
		buildReports()
		def report = Report.get(report1Id)
		authenticateAsAdmin()

		def userAuth = authenticateAsUser(false)

		when:
		aclUtilService.addPermission(report, USER, READ)
		aclUtilService.addPermission(report, USER, WRITE)

		then:
		aclUtilService.hasPermission userAuth, report, READ
		aclUtilService.hasPermission userAuth, report, WRITE

		when:
		aclUtilService.deletePermission(report, USER, READ)

		then:
		!aclUtilService.hasPermission(userAuth, report, READ)
		aclUtilService.hasPermission userAuth, report, WRITE
	}

	void 'cumulative permission'() {
		given:
		buildReports()
		def report = Report.get(report1Id)
		authenticateAsAdmin()

		def userAuth = authenticateAsUser(false)

		when:

		aclUtilService.addPermission(report, USER, new CumulativePermission().set(READ).set(WRITE))

		then:
		!aclUtilService.hasPermission(userAuth, report, READ)
		!aclUtilService.hasPermission(userAuth, report, WRITE)
		!aclUtilService.hasPermission(userAuth, Report.get(report2Id), READ)
	}

	@Issue('GPSPRINGSECURITYACL-23')
	void foo() {
		given:
		buildReports()

		when:
		String name = 'Report 1'
		Report report = new Report(name: name).save(failOnError: true)
		long reportId = report.id

		and:
		authenticateAsAdmin()
		aclUtilService.addPermission Report.findByName(name), USER, WRITE
		flushAndClear()
		report = Report.load(reportId)

		then:
		ProxyUtils.isProxy report.getClass()
		aclUtilService.hasPermission(authenticateAsUser(false), report, WRITE)
	}
}
