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
package grails.plugin.formfields.mock

import grails.gorm.annotation.AutoTimestamp
import grails.persistence.Entity

@Entity
class Cyborg extends Person {
	@AutoTimestamp(AutoTimestamp.EventType.CREATED) Date created
	@AutoTimestamp Date modified
}

@Entity
class Person {
    Salutation salutation
	String name
	String password
	Gender gender
	Date dateOfBirth
	Address address
	Map emails = [:]
	boolean minor
	Date lastUpdated
	String excludedProperty
	String displayFalseProperty
	Boolean grailsDeveloper
	Byte[] picture
	byte[] anotherPicture
	String biography

    transient String transientText = "transient text"

	static hasMany = [emails: String]
	static embedded = ['address']

	static constraints = {
        salutation nullable: true
		name blank: false
		dateOfBirth nullable: true
		address nullable: true
		excludedProperty nullable: true
		displayFalseProperty nullable: true, display: false
		grailsDeveloper nullable: true
		picture nullable: true
		anotherPicture nullable: true
		password password: true
		biography nullable: true, widget: 'textarea'
	}

	static scaffold = [exclude: ['excludedProperty']]
    static transients = ['transientText']
	def onLoad = {
		println "loaded"
	}

	@Override
	String toString() {
		name
	}
}
