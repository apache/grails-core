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

package org.grails.validation

import grails.persistence.Entity
import grails.testing.gorm.DataTest
import org.springframework.validation.Errors
import spock.lang.Specification

/**
 */
class CascadingErrorCountSpec extends Specification implements DataTest {

    void setupSpec() {
        mockDomains CascadingPerson, Name
    }

    void "Test that the error count is correct when validating sorted set"() {
        when:"A domain is created with an invalid collection and then validated"
            def person = new CascadingPerson(placeholder:"test")
            person.addToNames(new Name(name:null))
            person.validate()

            println "ERRORS ARE ${person.errors}"
        then:"The error count is correct"
            person.hasErrors() == true
            ((Errors)person.errors).getFieldError('names[0].name') != null
    }
}

@Entity
class CascadingPerson {
    String placeholder

    SortedSet<Name> names
    static hasMany = [ names: Name ]
}

@Entity
class Name implements Comparable<Name> {
    String name
    static belongsTo = [ person: CascadingPerson ]

    static constraints = {
        name(blank: false, nullable: false)
    }

    int compareTo(Name other) { other.name <=> name }
}
