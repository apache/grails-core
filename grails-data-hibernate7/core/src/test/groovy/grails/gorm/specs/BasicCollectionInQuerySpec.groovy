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
package grails.gorm.specs

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Reproduces https://github.com/apache/grails-core/issues/14610
 *
 * When a domain has hasMany to a basic type (String), using 'in' on
 * that collection property in criteria queries fails with
 * "Parameter #1 is not set".
 */
@Rollback
class BasicCollectionInQuerySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore =
        new HibernateDatastore(BcStudent)

    @Issue("https://github.com/apache/grails-core/issues/14610")
    def "in query on basic collection type should work"() {
        given:
        def s1 = new BcStudent(name: "Alice", email: "alice@test.com")
        s1.addToSchools("School1")
        s1.addToSchools("School2")
        s1.save()

        def s2 = new BcStudent(name: "Bob", email: "bob@test.com")
        s2.addToSchools("School2")
        s2.addToSchools("School3")
        s2.save()

        def s3 = new BcStudent(name: "Charlie", email: "charlie@test.com")
        s3.addToSchools("School3")
        s3.save(flush: true)

        when:
        def results = BcStudent.createCriteria().list {
            'in'('schools', ['School2'])
            projections {
                property 'email'
            }
        }

        then:
        results.sort() == ['alice@test.com', 'bob@test.com']
    }

    def "workaround using createAlias on basic collection"() {
        given:
        def s1 = new BcStudent(name: "Alice2", email: "alice2@test.com")
        s1.addToSchools("SchoolA")
        s1.addToSchools("SchoolB")
        s1.save()

        def s2 = new BcStudent(name: "Bob2", email: "bob2@test.com")
        s2.addToSchools("SchoolB")
        s2.save(flush: true)

        when:
        def results = BcStudent.createCriteria().list {
            createAlias("schools", "s")
            'in'("s", ["SchoolB"])
            projections {
                property 'email'
            }
        }

        then:
        results.sort() == ['alice2@test.com', 'bob2@test.com']
    }

    def "multiple in queries on same basic collection should not fail with duplicate alias"() {
        given:
        def s1 = new BcStudent(name: "Dave", email: "dave@test.com")
        s1.addToSchools("MIT")
        s1.addToSchools("Harvard")
        s1.save()

        def s2 = new BcStudent(name: "Eve", email: "eve@test.com")
        s2.addToSchools("Stanford")
        s2.addToSchools("Berkeley")
        s2.save()

        def s3 = new BcStudent(name: "Frank", email: "frank@test.com")
        s3.addToSchools("MIT")
        s3.addToSchools("Stanford")
        s3.save(flush: true)

        when:
        def results = BcStudent.createCriteria().list {
            or {
                'in'('schools', ['MIT'])
                'in'('schools', ['Stanford'])
            }
            projections {
                property 'email'
            }
        }

        then: "all matching students are found (duplicates possible from OR on join table)"
        results.unique().sort() == ['dave@test.com', 'eve@test.com', 'frank@test.com']
    }

    def "in query on basic collection with pre-existing alias should reuse it"() {
        given:
        def s1 = new BcStudent(name: "Grace", email: "grace@test.com")
        s1.addToSchools("Yale")
        s1.addToSchools("Princeton")
        s1.save()

        def s2 = new BcStudent(name: "Hank", email: "hank@test.com")
        s2.addToSchools("Princeton")
        s2.save()

        def s3 = new BcStudent(name: "Ivy", email: "ivy@test.com")
        s3.addToSchools("Columbia")
        s3.save(flush: true)

        when: "an alias is explicitly created before using in() with the raw property name"
        def results = BcStudent.createCriteria().list {
            createAlias("schools", "sch")
            'in'('schools', ['Princeton'])
            projections {
                property 'email'
            }
        }

        then: "the existing alias is reused instead of creating a duplicate"
        results.sort() == ['grace@test.com', 'hank@test.com']
    }
}

@Entity
class BcStudent {
    String name
    String email

    static hasMany = [schools: String]

    static constraints = {
        name blank: false
        email blank: false
    }
}
