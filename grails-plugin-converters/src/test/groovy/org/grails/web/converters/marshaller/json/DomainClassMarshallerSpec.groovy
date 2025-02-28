package org.grails.web.converters.marshaller.json

import grails.converters.JSON
import grails.converters.XML
import grails.core.DefaultGrailsApplication
import grails.persistence.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

class DomainClassMarshallerSpec extends Specification {


    void setup() {
    }

    void initJson(boolean domainClassname) {
        final initializer = new ConvertersConfigurationInitializer()
        def grailsApplication = new DefaultGrailsApplication(Author, Book, RenamedIdentifier)
        grailsApplication.config.setAt("grails.converters.json.domain.include.class", domainClassname)
        grailsApplication.config.setAt("grails.converters.xml.domain.include.class", domainClassname)
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext("json")
        mappingContext.addPersistentEntities(Book, Author, RenamedIdentifier)
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> {
                mappingContext
            }
        })
        grailsApplication.setMappingContext(mappingContext)
        initializer.grailsApplication = grailsApplication
        initializer.initialize()

    }

    @Unroll
    void "Test DomainClassMarshaller's should maintain order of relations"() {
        def json, xml

        when:
        initJson(false)
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
        JSON.use('deep') {
            json = book as JSON
        }
        XML.use('deep') {
            xml = book as XML
        }

        then:
        json.toString() == expectedJson
        xml.toString() == expectedXml

        where:
        authors                                                      | expectedJson                                                                                                                     | expectedXml
        [new Author(id: 1, name: 'a'), new Author(id: 2, name: 'b')] | '{"id":1,"authorsSet":[{"id":1,"name":"a"},{"id":2,"name":"b"}],"authorsMap":{"a":{"id":1,"name":"a"},"b":{"id":2,"name":"b"}}}' | '<?xml version="1.0" encoding="UTF-8"?><book id="1"><authorsSet><author id="1"><name>a</name></author><author id="2"><name>b</name></author></authorsSet><authorsMap><entry key="a" id="1"><name>a</name></entry><entry key="b" id="2"><name>b</name></entry></authorsMap></book>'
        [new Author(id: 2, name: 'b'), new Author(id: 1, name: 'a')] | '{"id":1,"authorsSet":[{"id":1,"name":"b"},{"id":2,"name":"a"}],"authorsMap":{"b":{"id":1,"name":"b"},"a":{"id":2,"name":"a"}}}' | '<?xml version="1.0" encoding="UTF-8"?><book id="1"><authorsSet><author id="1"><name>b</name></author><author id="2"><name>a</name></author></authorsSet><authorsMap><entry key="b" id="1"><name>b</name></entry><entry key="a" id="2"><name>a</name></entry></authorsMap></book>'
    }

    void "test marshaller should render the ID properly"() {
        initJson(false)
        when:
        RenamedIdentifier ri = new RenamedIdentifier(newId: 3, name: "Sally")

        then:
        def jsonString = new JSON(ri).toString()
        jsonString.contains('"newId":3')
        jsonString.contains('"name":"Sally"')
        jsonString.contains('"version":null')
    }

    void "test marshallers generate class names when options are set"() {
        def json, xml
        initJson(true)
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
        JSON.use('deep') {
            json = book as JSON
        }
        XML.use('deep') {
            xml = book as XML
        }

        then:
        json.toString() == expectedJson
        xml.toString() == expectedXml

        where:
        authors                                                      | expectedJson                                                                                                                                                                                                                                                                                                                                                                                                                          | expectedXml
        [new Author(id: 1, name: 'a'), new Author(id: 2, name: 'b')] | '{"class":"org.grails.web.converters.marshaller.json.Book","id":1,"authorsSet":[{"class":"org.grails.web.converters.marshaller.json.Author","id":1,"name":"a"},{"class":"org.grails.web.converters.marshaller.json.Author","id":2,"name":"b"}],"authorsMap":{"a":{"class":"org.grails.web.converters.marshaller.json.Author","id":1,"name":"a"},"b":{"class":"org.grails.web.converters.marshaller.json.Author","id":2,"name":"b"}}}' | '<?xml version="1.0" encoding="UTF-8"?><book id="1" class="org.grails.web.converters.marshaller.json.Book"><authorsSet><author id="1" class="org.grails.web.converters.marshaller.json.Author"><name>a</name></author><author id="2" class="org.grails.web.converters.marshaller.json.Author"><name>b</name></author></authorsSet><authorsMap><entry key="a" id="1" class="org.grails.web.converters.marshaller.json.Author"><name>a</name></entry><entry key="b" id="2" class="org.grails.web.converters.marshaller.json.Author"><name>b</name></entry></authorsMap></book>'

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

@Entity
class RenamedIdentifier {

    Long newId
    String name

    static mapping = {
        version false
        id name: 'newId'
    }
}