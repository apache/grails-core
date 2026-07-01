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

package com.test

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes=['series', 'username'])
@ToString(includes=['series', 'username'], cache=true, includeNames=true, includePackage=false)
class PersistentLogin implements Serializable {

	private static final long serialVersionUID = 1

	String series
	String username
	String token
	Date lastUsed

	static constraints = {
		series maxSize: 64
		token maxSize: 64
		username maxSize: 64
	}

	static mapping = {
		table 'persistent_login'
		id name: 'series', generator: 'assigned'
		version false
	}
}
