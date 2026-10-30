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
package org.grails.web.converters.marshaller.xml

import grails.converters.XML
import grails.core.DefaultGrailsApplication
import grails.persistence.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

class XmlDomainClassMarshallerSpec extends Specification {

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void initXml(boolean domainClassname) {
        initXml(['grails.converters.xml.domain.include.class': domainClassname])
    }

    void initXml(Map<String, Object> settings) {
        def grailsApplication = new DefaultGrailsApplication(Author, Book)
        settings.each { String key, Object value -> grailsApplication.config.setAt(key, value) }
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext("json")
        mappingContext.addPersistentEntities(Book, Author)
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> {
                mappingContext
            }
        })
        grailsApplication.setMappingContext(mappingContext)
        new XmlConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()
    }

    @Unroll
    void "Test DomainClassMarshaller's should maintain order of relations"() {
        def xml

        when:
        initXml(false)
        int id = 1
        def book = new Book(
                id: 1, version: 2,
                authorsSet: authors.collect { it.id = id++; it },
                authorsMap: authors.inject([:]) { acc, val ->
                    acc[val.name] = val
                    acc
                }
        )
        book.id = 1
        XML.use('deep') {
            xml = book as XML
        }

        then:
        xml.toString() == expectedXml

        where:
        authors                                                      | expectedXml
        [new Author(id: 1, name: 'a'), new Author(id: 2, name: 'b')] | '<?xml version="1.0" encoding="UTF-8"?><book id="1"><authorsSet><author id="1"><name>a</name></author><author id="2"><name>b</name></author></authorsSet><authorsMap><entry key="a" id="1"><name>a</name></entry><entry key="b" id="2"><name>b</name></entry></authorsMap></book>'
        [new Author(id: 2, name: 'b'), new Author(id: 1, name: 'a')] | '<?xml version="1.0" encoding="UTF-8"?><book id="1"><authorsSet><author id="1"><name>b</name></author><author id="2"><name>a</name></author></authorsSet><authorsMap><entry key="b" id="1"><name>b</name></entry><entry key="a" id="2"><name>a</name></entry></authorsMap></book>'
    }

    void "test marshallers generate class names when options are set"() {
        def xml
        initXml(true)
        when:
        int id = 1
        def book = new Book(
                id: 1, version: 2,
                authorsSet: authors.collect { it.id = id++; it },
                authorsMap: authors.inject([:]) { acc, val ->
                    acc[val.name] = val
                    acc
                }
        )
        book.id = 1
        XML.use('deep') {
            xml = book as XML
        }

        then:
        xml.toString() == expectedXml

        where:
        authors                                                      | expectedXml
        [new Author(id: 1, name: 'a'), new Author(id: 2, name: 'b')] | '<?xml version="1.0" encoding="UTF-8"?><book id="1" class="org.grails.web.converters.marshaller.xml.Book"><authorsSet><author id="1" class="org.grails.web.converters.marshaller.xml.Author"><name>a</name></author><author id="2" class="org.grails.web.converters.marshaller.xml.Author"><name>b</name></author></authorsSet><authorsMap><entry key="a" id="1" class="org.grails.web.converters.marshaller.xml.Author"><name>a</name></entry><entry key="b" id="2" class="org.grails.web.converters.marshaller.xml.Author"><name>b</name></entry></authorsMap></book>'

    }

    @Unroll
    void "the class name is written only by the deep named configuration, as Grails 8 wrote it, with #settings"() {
        given:
        initXml(settings)
        def author = new Author(name: 'a')
        author.id = 2
        def book = new Book(authorsSet: [author], authorsMap: [:])
        book.id = 1

        when:
        String rendered = (book as XML).toString()
        String deep = null
        XML.use('deep') {
            deep = (book as XML).toString()
        }

        then: 'the default rendering, shallow or deep, has no class attribute'
        !rendered.contains('class=')
        rendered.contains(defaultAuthor)

        and: 'XML.use(\'deep\') writes it on the instance and its associations'
        deep.contains('<book id="1" class="org.grails.web.converters.marshaller.xml.Book">')
        deep.contains('<author id="2" class="org.grails.web.converters.marshaller.xml.Author"><name>a</name></author>')

        where:
        settings                                                                                  | defaultAuthor
        ['grails.converters.xml.domain.include.class': true]                                      | '<author id="2" />'
        ['grails.converters.domain.include.class': true]                                          | '<author id="2" />'
        ['grails.converters.domain.include.class': true, 'grails.converters.xml.default.deep': true] | '<author id="2"><name>a</name></author>'
    }

    void "the XML setting wins over the shared one for the deep named configuration"() {
        given:
        initXml(['grails.converters.domain.include.class': true, 'grails.converters.xml.domain.include.class': false])
        def book = new Book(authorsSet: [], authorsMap: [:])
        book.id = 1

        when:
        String deep = null
        XML.use('deep') {
            deep = (book as XML).toString()
        }

        then:
        deep.contains('<book id="1">')
    }
}

@Entity
class Author {
    Long id
    Long version
    String name
}

@Entity
class Book {
    static hasMany = [authorsSet: Author, authorsMap: Author]
    Long id
    Long version
    List authorsSet
    Map authorsMap
}
