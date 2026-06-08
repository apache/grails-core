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

package pages.requestmap

import geb.Module
import pages.ScaffoldPage

class ListRequestmapPage extends ScaffoldPage {

	static url = 'testRequestmap'

	static at = {
		title ==~ /TestRequestmap List/
	}

	static content = {
		newRequestmapButton(to: CreateRequestmapPage) { $('a', text: 'New TestRequestmap') }
		requestmapTable { $('div.content table', 0) }
		requestmapRows(required: false) { requestmapTable.find('tbody').find('tr') }
		requestmapRow { i -> requestmapRows[i].module(RequestmapRow) }
	}
}

class RequestmapRow extends Module {
	static content = {
		cell { i -> $('td', i) }
		cellText { i -> cell(i).text() }
		cellHrefText{ i -> cell(i).find('a').text() }

		configAttribute { cellText(1) }
		showLink(to: ShowRequestmapPage) { cell(0).find('a') }
	}
}
