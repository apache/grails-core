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

package pages.user

import geb.Module
import pages.ScaffoldPage

class ListUserPage extends ScaffoldPage {

	static url = 'testUser'

	static at = {
		title ==~ /TestUser List/
	}

	static content = {
		newUserButton(to: CreateUserPage) { $('a', text: 'New TestUser') }
		userTable { $('div.list table', 0) }
		userRow { i -> userRows[i].module UserRow }
		userRows(required: false) { userTable.find('tbody').find('tr') }
	}
}

class UserRow extends Module {
	static content = {
		cell { i -> $('td', i) }
		cellText { i -> cell(i).text() }
		cellHrefText{ i -> cell(i).find('a').text() }
		username { cellText(1) }
		userEnabled { 'True' == cellText(2) }
		showLink(to: ShowUserPage) { cell(0).find('a') }
	}
}
