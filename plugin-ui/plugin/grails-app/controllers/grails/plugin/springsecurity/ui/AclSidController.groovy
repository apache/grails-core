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
package grails.plugin.springsecurity.ui

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class AclSidController extends AbstractS2UiDomainController {

	def create() {
		super.create()
	}

	def save() {
		withForm {
			doSave uiAclStrategy.saveAclSid(params)
		}.invalidToken {
			doSaveWithInvalidToken(params.username)
		}
	}

	def edit() {
		super.edit()
	}

	def update() {
		withForm {
			doUpdate { aclSid ->
				uiAclStrategy.updateAclSid params, aclSid
			}
		}.invalidToken {
			doUpdateWithInvalidToken(params.username)
		}
	}

	def delete() {
		withForm {
			tryDelete { aclSid ->
				uiAclStrategy.deleteAclSid aclSid
			}
		}.invalidToken {
			doDeleteWithInvalidToken()
		}
	}

	def search() {
		if (!isSearch()) {
			// show the form
			return
		}

		def results = doSearch { ->
			eqBoolean 'principal', delegate
			like 'sid', delegate
		}

		renderSearch([results: results, totalCount: results.totalCount],
		             'principal', 'sid')
	}

	protected Class<?> getClazz() { AclSid }
	protected String getClassLabelCode() { 'aclSid.label' }
	protected String getSimpleClassName() { 'AclSid' }
	protected Map model(aclSid, String action) {
		[aclSid: aclSid]
	}
}
