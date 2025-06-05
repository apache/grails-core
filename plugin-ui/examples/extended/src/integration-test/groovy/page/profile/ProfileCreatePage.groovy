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
import page.CreatePage

class ProfileCreatePage extends AbstractSecurityPage {

	static at = { title == 'Create Profile' }
	static url = 'profile/create'
	static content = {
		myQuestion { $('#myQuestion1').module(TextInput) }
		myQuestion2 { $('#myQuestion2').module(TextInput) }
		myAnswer2 { $('#myAnswer2').module(TextInput) }
		myAnswer { $('#myAnswer1').module(TextInput) }
		submitBtn { $('#create')}
	}

	void create(String userName) {
		def userSelect = $(name: 'user.id').module(Select)
		userSelect.selected = userName
		myQuestion = 'Count to 8'
		myQuestion2 = 'Count to 4'
		myAnswer2  = '1234'
		myAnswer = '12345678'
		submit()
	}
}
