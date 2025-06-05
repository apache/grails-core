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

package page.user

import geb.module.RadioButtons
import geb.module.TextInput
import page.SearchPage

class UserSearchPage extends SearchPage {

	static url = 'user/search'
	static typeName = { 'User' }
	static content = {
		username { $('#username').module(TextInput) }
		enabled { $(name: 'enabled').module(RadioButtons) }
		accountExpired { $(name: 'accountExpired').module(RadioButtons) }
		accountLocked { $(name: 'accountLocked').module(RadioButtons) }
		passwordExpired { $(name: 'passwordExpired').module(RadioButtons) }
	}
}
