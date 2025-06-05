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

package spec

import grails.testing.mixin.integration.Integration

@Integration
class ExtendedMenuSpec extends AbstractSecuritySpec {

	void testIndex() {
		when:
		browser.go('')
		String html = pageSource

		then:
		html.contains 'Spring Security Management Console'

		html.contains 'Users'

		html.contains 'Roles'

		html.contains 'Requestmaps'

		html.contains 'Registration Code'

		html.contains 'Configuration'
		html.contains 'Mappings'
		html.contains 'Current Authentication'
		html.contains 'User Cache'
		html.contains 'Filter Chains'
		html.contains 'Logout Handlers'
		html.contains 'Voters'
		html.contains 'Authentication Providers'
		html.contains 'Profile Questions'
		html.contains 'Persistent Logins'

		html.contains 'ACL'
		html.contains 'SID'
		html.contains 'OID'
		html.contains 'Entry'
	}
}
