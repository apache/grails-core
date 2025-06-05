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
package test

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * @author Burt Beckwith
 */
@EqualsAndHashCode(includes='auth')
@ToString(includes='auth', includeNames=true, includePackage=false)
class TestRole implements Serializable {

	private static final long serialVersionUID = 1

	String auth
	String description

	static constraints = {
		auth blank: false, unique: true
	}

	static mapping = {
		cache true
	}
}
