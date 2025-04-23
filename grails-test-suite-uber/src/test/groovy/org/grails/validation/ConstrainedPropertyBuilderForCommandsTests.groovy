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
import grails.testing.gorm.DomainUnitTest
import grails.validation.Validateable
import spock.lang.Specification

/**
 * Tests constraints building specific for command objects
 */
class ConstrainedPropertyBuilderForCommandsTests extends Specification implements DomainUnitTest<ConstraintsPerson> {

    void testImportFrom_AllConstraints_ConstraintsExist() {
        when:
        def personCommandConstraints = PersonAllConstraintsNoNormalConstraintsCommand.constraintsMap
        
        then:
        personCommandConstraints != null
        personCommandConstraints.size() == 5
        personCommandConstraints.get("importFrom") == null
        personCommandConstraints.get("email") != null
    }

    void testImportFrom_AllConstraints_Validation() {
        given:
        def personCommand = new PersonAllConstraintsNoNormalConstraintsCommand()

        when:
        personCommand.firstName = "firstName"
        personCommand.lastName = "lastName"
        personCommand.validate()

        then:
        !personCommand.hasErrors()

        when:
        personCommand.clearErrors()
        personCommand.firstName = null
        personCommand.validate()

        then:
        personCommand.hasErrors()
        personCommand.getErrors().getErrorCount() == 1
        personCommand.getErrors().getFieldErrors("firstName").size() == 1
        personCommand.getErrors().getFieldErrors("firstName")[0].getRejectedValue() == null
    }

    void testImportFrom_SomeConstraints_ConstraintsExist() {
        when:
        def personCommandConstraints = PersonSomeConstraintsNoNormalConstraintsCommand.constraintsMap

        then:
        personCommandConstraints != null
        personCommandConstraints.size() == 2
        personCommandConstraints.get("importFrom") == null
        personCommandConstraints.get("firstName") != null
    }

    void testImportFrom_SomeConstraints_Validation() {
        given:
        def personCommand = new PersonSomeConstraintsNoNormalConstraintsCommand()

        when:
        personCommand.firstName = "firstName"
        personCommand.lastName = "lastName"
        personCommand.validate()

        then:
        !personCommand.hasErrors()

        when:
        personCommand.clearErrors()
        personCommand.firstName = null
        personCommand.validate()

        then:
        personCommand.hasErrors()
        personCommand.getErrors().getErrorCount() == 1
        personCommand.getErrors().getFieldErrors("firstName").size() == 1
        personCommand.getErrors().getFieldErrors("firstName")[0].getRejectedValue() == null

        when:
        // Now check that everything is ok with domain class
        def person = new ConstraintsPerson()

        person.firstName = "firstName"
        person.lastName = "lastName"
        person.email = "someemail@some.net"
        person.validate()

        then:
        !person.hasErrors()

        when:
        person.clearErrors()
        person.email = "wrongEmail"
        person.validate()

        then:
        person.hasErrors()
        person.getErrors().getErrorCount() == 1
        person.getErrors().getFieldErrors("email").size() == 1
        person.getErrors().getFieldErrors("email")[0].getRejectedValue() == "wrongEmail"
    }

    void testImportFrom_AllConstraints_ConstraintsExist_NormalConstraintsFirst() {
        when:
        def personCommandConstraints = PersonAllConstraintsWithNormalConstraintsFirstCommand.constraintsMap

        then:
        personCommandConstraints != null
        personCommandConstraints.size() == 5
        personCommandConstraints.get("importFrom") == null
        personCommandConstraints.get("telephone") != null

        personCommandConstraints.get("firstName").getAppliedConstraint("maxSize").getParameter() == 30
        personCommandConstraints.get("lastName").getAppliedConstraint("maxSize").getParameter() == 50
        personCommandConstraints.get("telephone").getAppliedConstraint("matches").getParameter() == "123123"
    }

    void testImportFrom_AllConstraints_Validation_NormalConstraintsFirst() {
        given:
        def personCommand = new PersonAllConstraintsWithNormalConstraintsFirstCommand()

        when:
        personCommand.firstName = "firstName"
        personCommand.lastName = "lastName"
        personCommand.validate()

        then:
        !personCommand.hasErrors()

        when:
        personCommand.clearErrors()
        personCommand.firstName = null
        personCommand.lastName = null
        personCommand.validate()

        then:
        personCommand.hasErrors()
        personCommand.getErrors().getErrorCount() == 2

        when:
        // Now check that everything is ok with domain class
        def person = new ConstraintsPerson()

        person.firstName = "firstName"
        person.lastName = "lastName"
        person.email = "someemail@some.net"
        person.validate()

        then:
        !person.hasErrors()

        when:
        person.clearErrors()
        person.firstName  = null
        person.email = "wrongEmail"
        person.validate()

        then:
        person.hasErrors()
        person.getErrors().getErrorCount() == 2
        person.getErrors().getFieldErrors("firstName").size() == 1
        person.getErrors().getFieldErrors("firstName")[0].getRejectedValue() == null
        person.getErrors().getFieldErrors("email").size() == 1
        person.getErrors().getFieldErrors("email")[0].getRejectedValue() == "wrongEmail"
    }

    void testImportFrom_AllConstraints_ConstraintsExist_NormalConstraintsLast() {
        when:
        def personCommandConstraints = PersonAllConstraintsWithNormalConstraintsLastCommand.constraintsMap

        then:
        personCommandConstraints != null
        personCommandConstraints.size() == 5
        personCommandConstraints.get("importFrom") == null
        personCommandConstraints.get("telephone") != null

        personCommandConstraints.get("firstName").getAppliedConstraint("maxSize").getParameter() == 10
        personCommandConstraints.get("lastName").getAppliedConstraint("maxSize").getParameter() == 20
        personCommandConstraints.get("telephone").getAppliedConstraint("matches").getParameter() == "123123"
    }

    void testImportFrom_AllConstraints_Validation_NormalConstraintsLast() {
        given:
        def personCommand = new PersonAllConstraintsWithNormalConstraintsLastCommand()

        when:
        personCommand.firstName = null
        personCommand.lastName = null
        personCommand.email = "someemail@some.net"
        personCommand.validate()

        then:
        !personCommand.hasErrors()

        when:
        personCommand.clearErrors()
        personCommand.firstName = null
        personCommand.lastName = null
        personCommand.email = "wrongEmail"
        personCommand.validate()

        then:
        personCommand.hasErrors()
        personCommand.getErrors().getErrorCount() == 1

        when:
        // Now check that everything is ok with domain class
        def person = new ConstraintsPerson()

        person.firstName = "firstName"
        person.lastName = "lastName"
        person.email = "someemail@some.net"
        person.validate()

        then:
        !person.hasErrors()

        when:
        person.clearErrors()
        person.firstName  = null
        person.email = "wrongEmail"
        person.validate()

        then:
        person.hasErrors()
        person.getErrors().getErrorCount() == 2
        person.getErrors().getFieldErrors("firstName").size() == 1
        person.getErrors().getFieldErrors("firstName")[0].getRejectedValue() == null
        person.getErrors().getFieldErrors("email").size() == 1
        person.getErrors().getFieldErrors("email")[0].getRejectedValue() == "wrongEmail"
    }

    void testImportFrom_AllConstraints_ConstraintsExist_Including() {
        when:
        def personCommandConstraints = PersonAllConstraintsNoNormalConstraintsIncludingCommand.constraintsMap
        def emailConstraint = personCommandConstraints.get('email')

        then:
        personCommandConstraints != null
        personCommandConstraints.size() == 5
        personCommandConstraints.get("importFrom") == null
        personCommandConstraints.get("firstName") != null

        emailConstraint != null
        !emailConstraint.hasAppliedConstraint('email')
        !emailConstraint.hasAppliedConstraint('blank')
        emailConstraint.hasAppliedConstraint('nullable')
    }

    void testImportFrom_AllConstraints_ConstraintsExist_Excluding() {
        when:
        def personCommandConstraints = PersonAllConstraintsNoNormalConstraintsExcludingCommand.constraintsMap

        then:
        personCommandConstraints != null
        personCommandConstraints.get("importFrom") == null
        personCommandConstraints.size() == 5

        when:
        def firstNameConstraint = personCommandConstraints.get("firstName")

        then:
        firstNameConstraint != null
        firstNameConstraint.hasAppliedConstraint('nullable')
        !firstNameConstraint.hasAppliedConstraint('maxSize')

        when:
        def lastNameConstraint = personCommandConstraints.get("lastName")

        then:
        lastNameConstraint != null
        lastNameConstraint.hasAppliedConstraint('nullable')
        !lastNameConstraint.hasAppliedConstraint('maxSize')

        when:
        def emailConstraint = personCommandConstraints.get("email")

        then:
        emailConstraint != null
        emailConstraint.hasAppliedConstraint('email')
    }

    void testImportFrom_AllConstraints_ConstraintsExist_IncludingByRegexp() {
        when:
        def personCommandConstraints = PersonAllConstraintsNoNormalConstraintsIncludingByRegexpCommand.constraintsMap

        then:
        personCommandConstraints != null
        personCommandConstraints.size() == 5
        personCommandConstraints.get("importFrom") == null
        personCommandConstraints.get("firstName") != null
        personCommandConstraints.get("lastName") != null
        personCommandConstraints.get("middleName") != null

        when:
        def emailConstraint = personCommandConstraints.get('email')

        then:
        emailConstraint != null
        !emailConstraint.hasAppliedConstraint('email')
        !emailConstraint.hasAppliedConstraint('blank')
        emailConstraint.hasAppliedConstraint('nullable')
    }

    void testImportFrom_AllConstraints_ConstraintsExist_IncludingExcludingByRegexp() {
        when:
        def personCommandConstraints = PersonAllConstraintsNoNormalConstraintsIncludingExcludingByRegexpCommand.constraintsMap

        then:
        personCommandConstraints != null
        personCommandConstraints.size() == 5
        personCommandConstraints.get("importFrom") == null
        personCommandConstraints.get("firstName") != null
        personCommandConstraints.get("lastName") != null

        when:
        def emailConstraint = personCommandConstraints.get('email')

        then:
        emailConstraint != null
        !emailConstraint.hasAppliedConstraint('email')
        !emailConstraint.hasAppliedConstraint('blank')
        emailConstraint.hasAppliedConstraint('nullable')
    }
}

@Entity
class ConstraintsPerson {
    String firstName
    String lastName
    String middleName
    String telephone
    String email

    static constraints = {
        firstName(nullable:false, blank:false, maxSize:30)
        lastName(nullable:false, blank:false, maxSize:50)
        middleName(nullable:true, blank:false, notEqual:"myMiddleName")
        telephone(nullable:true, blank:false, matches:"123123")
        email(nullable:true, blank:false, email:true)
    }
}

class PersonAllConstraintsNoNormalConstraintsCommand implements Validateable {
    String firstName
    String lastName
    String middleName
    String telephone
    String email

    static constraints = {
        importFrom ConstraintsPerson
    }
}

class PersonSomeConstraintsNoNormalConstraintsCommand implements Validateable {
    String firstName
    String lastName

    static constraints = {
        importFrom ConstraintsPerson
    }
}

class PersonAllConstraintsWithNormalConstraintsFirstCommand implements Validateable {
    String firstName
    String lastName
    String middleName
    String telephone
    String email

    static constraints = {
        firstName(nullable:true, blank:true, maxSize:10)
        lastName(nullable:true, blank:true, maxSize:20)
        email(nullable:false, blank:true, email:true)

        importFrom ConstraintsPerson
    }
}

class PersonAllConstraintsWithNormalConstraintsLastCommand implements Validateable {
    String firstName
    String lastName
    String middleName
    String telephone
    String email

    static constraints = {
        importFrom ConstraintsPerson

        firstName(nullable:true, blank:true, maxSize:10)
        lastName(nullable:true, blank:true, maxSize:20)
        email(nullable:false, blank:true, email:true)
    }
}

class PersonAllConstraintsNoNormalConstraintsIncludingCommand implements Validateable {
    String firstName
    String lastName
    String middleName
    String telephone
    String email

    static constraints = {
        importFrom ConstraintsPerson, include:["firstName", "lastName"]
    }
}

class PersonAllConstraintsNoNormalConstraintsExcludingCommand implements Validateable {
    String firstName
    String lastName
    String middleName
    String telephone
    String email

    static constraints = {
        importFrom ConstraintsPerson, exclude:["firstName", "lastName"]
    }
}

class PersonAllConstraintsNoNormalConstraintsIncludingByRegexpCommand implements Validateable {
    String firstName
    String lastName
    String middleName
    String telephone
    String email

    static constraints = {
        importFrom ConstraintsPerson, include:[".*Name"]
    }
}

class PersonAllConstraintsNoNormalConstraintsIncludingExcludingByRegexpCommand implements Validateable {
    String firstName
    String lastName
    String middleName
    String telephone
    String email

    static constraints = {
        importFrom ConstraintsPerson, include:[".*Name"], exclude:["m.*Name"]
    }
}
