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
package extended.spec

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class ExtendedMenuSpec extends ContainerGebSpec {

	void testIndex() {
		when:
		go('')

		then:
			pageSource.contains('Spring Security Management Console')

			pageSource.contains('Users')

			pageSource.contains('Roles')

			pageSource.contains('Requestmaps')

			pageSource.contains('Registration Code')

			pageSource.contains('Configuration')
			pageSource.contains('Mappings')
			pageSource.contains('Current Authentication')
			pageSource.contains('User Cache')
			pageSource.contains('Filter Chains')
			pageSource.contains('Logout Handlers')
			pageSource.contains('Voters')
			pageSource.contains('Authentication Providers')
			pageSource.contains('Profile Questions')
			pageSource.contains('Persistent Logins')

			pageSource.contains('ACL')
			pageSource.contains('SID')
			pageSource.contains('OID')
			pageSource.contains('Entry')

	}
}
