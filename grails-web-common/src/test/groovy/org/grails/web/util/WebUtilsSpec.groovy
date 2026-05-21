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
package org.grails.web.util

import spock.lang.Issue
import spock.lang.Specification

/**
 * @Author Sudhir Nimavat
 */
class WebUtilsSpec extends Specification {

	@Issue("https://github.com/apache/grails-core/issues/10545")
	def testToQueryString() {
		given:
		Map params = ["name":"sudhir-nimavat", "address.zip":"12345"]

		when:
		String result = WebUtils.toQueryString(params)

		then:
		result.startsWith("?")
		def tokens = result[1..-1].split('&')
		tokens.find({ it == "name=sudhir-nimavat"}) != null
		tokens.find({ it == "address.zip=12345"}) != null
	}
}
