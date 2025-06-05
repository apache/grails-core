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
package grails.plugin.springsecurity.ui.strategy

import grails.plugin.springsecurity.ui.SpringSecurityUiService
import groovy.transform.CompileStatic

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class DefaultAclStrategy implements AclStrategy {

	SpringSecurityUiService springSecurityUiService

	def saveAclClass(Map properties) {
		springSecurityUiService.saveAclClass properties
	}

	void updateAclClass(Map properties, aclClass) {
		springSecurityUiService.updateAclClass properties, aclClass
	}

	void deleteAclClass(aclClass) {
		springSecurityUiService.deleteAclClass aclClass
	}

	def saveAclEntry(Map properties) {
		springSecurityUiService.saveAclEntry properties
	}

	void updateAclEntry(Map properties, aclEntry) {
		springSecurityUiService.updateAclEntry properties, aclEntry
	}

	void deleteAclEntry(aclEntry) {
		springSecurityUiService.deleteAclEntry aclEntry
	}

	def saveAclSid(Map properties) {
		springSecurityUiService.saveAclSid properties
	}

	void updateAclSid(Map properties, aclSid) {
		springSecurityUiService.updateAclSid properties, aclSid
	}

	void deleteAclSid(aclSid) {
		springSecurityUiService.deleteAclSid aclSid
	}

	def saveAclObjectIdentity(Map properties) {
		springSecurityUiService.saveAclObjectIdentity properties
	}

	void updateAclObjectIdentity(Map properties, aclObjectIdentity) {
		springSecurityUiService.updateAclObjectIdentity properties, aclObjectIdentity
	}

	void deleteAclObjectIdentity(aclObjectIdentity) {
		springSecurityUiService.deleteAclObjectIdentity aclObjectIdentity
	}
}
