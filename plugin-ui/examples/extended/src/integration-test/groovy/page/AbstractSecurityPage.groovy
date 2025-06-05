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

package page

import geb.Page

abstract class AbstractSecurityPage extends Page {

	void submit() {
		submitBtn.click()
	}

	protected boolean assertContentContains(String expected) {
		assert $().text().contains(expected)
		true
	}

	protected boolean assertContentMatches(String regex) {
		assert $().text() ==~ regex
		true
	}

	protected boolean assertContentDoesNotContain(String unexpected) {
		assert !$().text().contains(unexpected)
		true
	}
}
