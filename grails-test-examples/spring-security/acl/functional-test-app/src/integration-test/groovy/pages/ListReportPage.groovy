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

package pages

import geb.Module

class ListReportPage extends ScaffoldPage {

	static url = 'report/list'

	static at = {
		title ==~ /Report List/
	}

	String convertToPath(Object[] args) {
		if (!args) {
			return ''
		}

		def params = args[0] as Map
		if (!params) {
			return ''
		}

		if (!params.containsKey('max')) {
			params.max = 1000
		}

		'?' + params.collect { key, value ->
			"${URLEncoder.encode(key.toString(), 'UTF-8')}=" +
					"${URLEncoder.encode(value?.toString() ?: '', 'UTF-8')}"
		}.join('&')
	}
	static content = {
		message { $('div.message').text() }
		nextLink { $('.nextLink') }
		reportTable { $('div.list table', 0) }
		reportRows { reportTable.find('tbody tr').moduleList(ReportRow) }
	}
}

class ReportRow extends Module {
	static content = {
		cell { int i -> $('td', i) }
		cellText { int i -> cell(i).text() }
		cellHrefText { int i -> cell(i).find('a').text() }
		name { cellText(1) }
		showLink(to: ShowReportPage) { cell(0).find('a') }
		grantLink(to: ReportGrantPage) { cell(2).find('a') }
	}
}
