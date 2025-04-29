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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class EmbeddedPropertyQuerySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses += [Book2, Author2]
    }

    void "Test eq query of embedded properties"() {
        given:
        def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
        book.save(flush: true, failOnError: true)
        manager.session.clear()
        when:
        book = Book2.createCriteria().get { eq 'publishPeriod.startDate', new Date(2012, 1, 1) }
        then:
        book != null
    }

    void "Test gt query of embedded properties"() {
        given:
        def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
        book.save(flush: true, failOnError: true)
        manager.session.clear()
        when:
        book = Book2.createCriteria().get { gt 'publishPeriod.startDate', new Date(2011, 1, 1) }
        then:
        book != null
    }

    void "Test ge query of embedded properties"() {
        given:
        def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
        book.save(flush: true, failOnError: true)
        manager.session.clear()
        when:
        book = Book2.createCriteria().get { ge 'publishPeriod.startDate', new Date(2012, 1, 1) }
        then:
        book != null
    }

    void "Test lt query of embedded properties"() {
        given:
        def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
        book.save(flush: true, failOnError: true)
        manager.session.clear()
        when:
        book = Book2.createCriteria().get { lt 'publishPeriod.startDate', new Date(2014, 1, 1) }
        then:
        book != null
    }

    void "Test le query of embedded properties"() {
        given:
        def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
        book.save(flush: true, failOnError: true)
        manager.session.clear()
        when:
        book = Book2.createCriteria().get { le 'publishPeriod.endDate', new Date(2013, 1, 1) }
        then:
        book != null
    }

    void "Test isNotNull query of embedded properties"() {
        given:
        def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
        book.save(flush: true, failOnError: true)
        manager.session.clear()
        when:
        book = Book2.createCriteria().get {
            isNotNull 'publishPeriod.endDate'
        }
        then:
        book != null
    }

    void "Test associated query of embedded property"() {
        given:
        def book = new Book2(name: 'Game of Thrones', publishPeriod: new Period(startDate: new Date(2012, 1, 1), endDate: new Date(2013, 1, 1)))
        def author = new Author2(name: 'George', books: [book])
        author.save(flush: true, failOnError: true)
        manager.session.clear()
        when:
        author = Author2.createCriteria().get {
            books {
                eq 'publishPeriod.startDate', new Date(2012, 1, 1)
            }
        }
        then:
        author != null
    }

}

@Entity
class Author2 {
    String name
    static hasMany = [books: Book2]
}

@Entity
class Book2 {
    String name
    Period publishPeriod

    static embedded = ['publishPeriod']
}

class Period {
    Date startDate
    Date endDate
}
