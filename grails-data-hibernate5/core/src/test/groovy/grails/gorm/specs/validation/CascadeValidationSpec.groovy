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

package grails.gorm.specs.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 04/05/2017.
 */
class CascadeValidationSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Business, Person, Employee)

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/926')
    void "validation cascades correctly"() {
        given: "an invalid business"
        Business b = new Business(name: null)

        and: "a valid employee that belongs to the business"
        Person p = new Employee(business: b)
        b.addToPeople(p)

        when:
        b.save()

        then:
        b.errors.hasFieldErrors('name')
        b.hasErrors()
    }
}
@Entity
class Business {

    String name

    static hasMany = [
            people: Person
    ]

}
@Entity
abstract class Person {

}

@Entity
class Employee extends Person {

    static belongsTo = [
            business: Business
    ]

}