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
package page.aclSid

import groovy.transform.Immutable

import geb.Page
import geb.module.RadioButtons
import geb.module.TextInput
import page.SearchPage

class AclSidSearchPage extends SearchPage {

	static url = 'aclSid/search'
	static typeName = { 'AclSid' }
	static content = {
		sid { $(name: 'sid').module(TextInput) }
		principal { $(name: 'principal').module(RadioButtons) }
	}

	AclSidSearchPage search(Form formData = null) {
		formData?.applyTo(this)
		submit(AclSidSearchPage)
	}

	@Immutable
	static class Form {

		String sid
		Principal principal

		<P extends Page> void applyTo(P page) {
			if (sid != null) page.$(name: 'sid').module(TextInput).text = sid
			if (principal != null) {
				// Temporary workaround for problem with Geb RadioButtons module
				//page.principal.checked = principal.value
				page.$('input', type: 'radio', name: 'principal', value: principal.value).click()
			}
		}

		enum Principal {

			TRUE('1'),
			FALSE('-1'),
			EITHER('0')

			final String value

			private Principal(String value) {
				this.value = value
			}
		}
	}
}
