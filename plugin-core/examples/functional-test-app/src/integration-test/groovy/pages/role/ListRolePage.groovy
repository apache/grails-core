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

package pages.role

import geb.Module
import pages.ScaffoldPage

class ListRolePage extends ScaffoldPage {

	static url = 'testRole'

	static at = {
		title ==~ /TestRole List/
	}

	static content = {
		newRoleButton(to: CreateRolePage) { $('a', text: 'New TestRole') }
		roleTable { $('div.content table', 0) }
		roleRow { i -> roleRows[i].module RoleRow }
		roleRows(required: false) { roleTable.find('tbody').find('tr') }
	}
}

class RoleRow extends Module {
	static content = {
		cell { i -> $('td', i) }
		cellText { i -> cell(i).text() }
		cellHrefText{ i -> cell(i).find('a').text() }
		authority { cellText(0) }
		showLink(to: ShowRolePage) { cell(0).find('a') }
	}
}
