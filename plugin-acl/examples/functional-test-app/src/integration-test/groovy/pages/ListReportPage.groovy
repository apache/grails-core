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

	static url = 'report/list?max=1000'

	static at = {
		title ==~ /Report List/
	}

	static content = {
		reportTable { $('div.list table', 0) }
		reportRow { i -> module ReportRow, reportRows[i] }
		reportRows(required: false) { reportTable.find('tbody').find('tr') }
	}
}

class ReportRow extends Module {
	static content = {
		cell { i -> $('td', i) }
		cellText { i -> cell(i).text() }
		cellHrefText{ i -> cell(i).find('a').text() }
		name { cellText(1) }
		showLink(to: ShowReportPage) { cell(0).find('a') }
		grantLink(to: ReportGrantPage) { cell(2).find('a') }
	}
}
