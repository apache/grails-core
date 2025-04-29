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
package grails.web.databinding

import groovy.xml.XmlSlurper
import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.validation.Validateable
import spock.lang.Issue
import spock.lang.Specification

class GrailsWebDataBinderBindingXmlSpec extends Specification implements DataTest {

    void setupSpec() {
        mockDomains(Writer, Book)
    }

    @Issue('GRAILS-10868')
    void 'Test binding a single XML child element to a List'() {
        given:
        def binder = new GrailsWebDataBinder(grailsApplication)
        def writer = new Writer(name: 'Writer One')
        
        when:
        def xml = new XmlSlurper().parseText("""
  <writer>
    <name>Writer One</name>
    <books>
        <book><title>Book One</title><publisher>Publisher One</publisher></book>
    </books>
  </writer>
""")
        binder.bind writer, xml
        
        then:
        writer.name == 'Writer One'
        writer.books.size() == 1
        writer.books[0].title == 'Book One'
        writer.books[0].publisher == 'Publisher One'
    }
    
    @Issue('GRAILS-11175')
    void 'Test binding a single XML child element to a Set of non domain objects'() {
        given:
        def binder = new GrailsWebDataBinder(grailsApplication)
        def book = new Book()
        
        when:
        def xml = new XmlSlurper().parseText("""
<stuff><reviewerNames><reviewer>Jeff</reviewer></reviewerNames></stuff>
""")
        binder.bind book, xml
        
        then:
        book.reviewerNames == ['Jeff'] as Set
    }
    
    @Issue('GRAILS-10868')
    void 'Test adding an existing element to a List by id'() {
        given:
        def binder = new GrailsWebDataBinder(grailsApplication)
        def writer = new Writer(name: 'Writer One')
        def originalBook = new Book(title: 'Book One', publisher: 'Publisher One')
        
        when:
        originalBook = originalBook.save()
        
        then:
        originalBook
        
        when:
        def xml = new XmlSlurper().parseText("""
  <writer>
    <name>Writer Two</name>
    <books>
        <book><id>${originalBook.id}</id><title>Updated Book One</title></book>
        <book><id>2</id><title>Book Two</title><publisher>Publisher Two</publisher></book>
    </books>
  </writer>
""")
        binder.bind writer, xml
        
        then:
        writer.name == 'Writer Two'
        writer.books.size() == 2
        writer.books[0].title == 'Updated Book One'
        writer.books[0].publisher == 'Publisher One'
        writer.books[1].publisher == 'Publisher Two'
        writer.books[1].title == 'Book Two'
    }
    
    @Issue('GRAILS-11175')
    void 'Test binding a single XML child element to a List in a non domain class'() {
        given:
        def binder = new GrailsWebDataBinder(grailsApplication)
        def obj = new CommandObject()
        
        when:
        def xml = new XmlSlurper().parseText("""
  <commandObject>
    <somethings>
        <something><name>One</name></something>
    </somethings>
  </commandObject>
""")
        binder.bind obj, xml
        
        then:
        !obj.hasErrors()
        obj.somethings?.size() == 1
        obj.somethings[0].name == 'One'
    }
    
    @Issue('GRAILS-11175')
    void 'Test binding multiple XML child elements to a List in a non domain class'() {
        given:
        def binder = new GrailsWebDataBinder(grailsApplication)
        def obj = new CommandObject()
        
        when:
        def xml = new XmlSlurper().parseText("""
  <commandObject>
    <somethings>
        <something><name>One</name></something>
        <something><name>Two</name></something>
    </somethings>
  </commandObject>
""")
        binder.bind obj, xml
        
        then:
        !obj.hasErrors()
        obj.somethings?.size() == 2
        obj.somethings[0].name == 'One'
        obj.somethings[1].name == 'Two'
    }
}

class CommandObject implements Validateable {
    List<Something> somethings
}

class Something {
    String name
}

@Entity
class Writer {
    String name
    List books
    static hasMany = [books: Book]
}

@Entity
class Book {
    String publisher
    String title
    Set<String> reviewerNames
}
