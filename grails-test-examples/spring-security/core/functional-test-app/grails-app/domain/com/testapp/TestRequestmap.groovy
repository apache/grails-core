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

package com.testapp

import org.springframework.http.HttpMethod

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode(includes=['configAttribute', 'httpMethod', 'url'])
@ToString(includes=['configAttribute', 'httpMethod', 'url'], cache=true, includeNames=true, includePackage=false)
class TestRequestmap implements Serializable {

	private static final long serialVersionUID = 1

	String configAttribute
	HttpMethod httpMethod
	String url

	static constraints = {
		configAttribute nullable: false, blank: false
		httpMethod nullable: true
		url nullable: false, blank: false, unique: 'httpMethod'
	}

	static mapping = {
		cache true
	}
}
