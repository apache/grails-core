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

package page.profile

import geb.module.Select
import geb.module.TextInput
import page.AbstractSecurityPage

class ProfileEditPage extends AbstractSecurityPage {

	static url = 'profile/edit'
	static at = { title == 'Edit Profile' }
	static content = {
		myQuestion { $('#myQuestion1').module(TextInput) }
		myQuestion2 { $('#myQuestion2').module(TextInput) }
		myAnswer2 { $('#myAnswer2').module(TextInput) }
		myAnswer { $('#myAnswer1').module(TextInput) }
		submitBtn { $('#update')}
	}

	void updateProfile(String userName) {
		def userSelect = $(name: 'user.id').module(Select)
		userSelect.selected  = userName
		myQuestion = 'Count to 4'
		myQuestion2 = 'Count to 5'
		myAnswer2  = '12345'
		myAnswer = '1234'
		submit()
	}

	void deleteProfile() {
		$('#deleteButton').click()
		waitFor {
			$('span', text: 'Are you sure?')
		}
		$('button', text: 'Delete').click()
	}
}