package grails.plugin.json.view

import com.fasterxml.jackson.databind.ObjectMapper
import grails.gorm.annotation.Entity
import grails.plugin.json.view.test.JsonViewTest
import org.grails.testing.GrailsUnitTest
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class EmbeddedAssociationsSpec extends Specification implements JsonViewTest, GrailsUnitTest {

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    void 'Test render domain object with embedded associations'() {
        given: 'A domain class with embedded associations'
        mappingContext.addPersistentEntities(Person)
        def p = new Person(name: 'Robert')
        p.homeAddress = new Address(postCode: '12345')
        p.otherAddresses = [new Address(postCode: '6789'), new Address(postCode: '54321')]
        p.nickNames = ['Rob','Bob']

        when: 'A an instance with embedded associations is rendered'
        def result = render('''
            import grails.plugin.json.view.*
            
            model {
                Person person
            }
            json g.render(person)
            
        ''', [person: p])

        then: 'The result is correct'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "otherAddresses": [
                    {"postCode": "6789"},
                    {"postCode": "54321"}
                ],
                "name": "Robert",
                "nickNames": ["Rob", "Bob"],
                "homeAddress": {"postCode": "12345"}
            }
        ''')
    }

    void 'Test render domain object with embedded associations in json api'() {
        given: 'A domain class with embedded associations'
        mappingContext.addPersistentEntities(Person)
        def p = new Person(name: 'Robert')
        p.id = 2
        p.homeAddress = new Address(postCode: '12345')
        p.otherAddresses = [new Address(postCode: '6789'), new Address(postCode: '54321')]
        p.nickNames = ['Rob', 'Bob']

        when: 'A an instance with embedded assocations is rendered'
        def result = render('''
            import grails.plugin.json.view.*
            
            model {
                Person person
            }
            json jsonapi.render(person)
        ''', [person: p])

        then: 'The result is correct'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "data": {
                    "type": "person",
                    "id": "2",
                    "attributes": {
                        "otherAddresses": [
                            {"postCode": "6789"},
                            {"postCode": "54321"}
                        ],
                        "name": "Robert",
                        "nickNames": ["Rob", "Bob"],
                        "homeAddress": {
                            "postCode": "12345"
                        }
                    }
                },
                "links": {
                    "self": "/person/2"
                }
            }
        ''')
    }

    @Issue('https://github.com/grails/grails-views/issues/171')
    void 'test render domain object with embedded associations and include'() {
        given: 'a domain class with embedded associations'
        mappingContext.addPersistentEntities(Person)
        def p = new Person(name: 'Robert')
        p.homeAddress = new Address(postCode: '12345')
        p.otherAddresses = [new Address(postCode: '6789'), new Address(postCode: '54321')]
        p.nickNames = ['Rob', 'Bob']

        when: 'an instance with embedded associations is rendered'
        def result = render('''
            import grails.plugin.json.view.*
            
            model {
                Person person
            }
            json g.render(person, [includes: ['name', 'homeAddress']])
        ''', [person: p])

        then: 'the result is correct'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "name": "Robert",
                "homeAddress": {"postCode": "12345"}
            }
        ''')
    }

    @Issue('https://github.com/grails/grails-views/issues/171')
    void 'test render domain object with embedded associations and include in json api'() {
        given: 'a domain class with embedded associations'
        mappingContext.addPersistentEntities(Person)
        def p = new Person(name: 'Robert')
        p.id = 4
        p.homeAddress = new Address(postCode: '12345')
        p.otherAddresses = [new Address(postCode: '6789'), new Address(postCode: '54321')]
        p.nickNames = ['Rob', 'Bob']

        when: 'an instance with embedded associations is rendered'
        def result = render('''
            import grails.plugin.json.view.*
            
            model {
                Person person
            }
            json jsonapi.render(person, [includes: ['name', 'homeAddress']])
        ''', [person: p])

        then: 'the result is correct'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "data": {
                    "type": "person",
                    "id": "4",
                    "attributes": {
                        "name": "Robert",
                        "homeAddress": {"postCode": "12345"}
                    }
                },
                "links": {
                    "self": "/person/4"
                }
            }
        ''')
    }
}


@Entity
class Person {
    String name
    Address homeAddress
    List<Address> otherAddresses = []
    List<String> nickNames = []

    @SuppressWarnings('unused')
    static embedded = ['homeAddress', 'otherAddresses']
}

class Address {
    String postCode
}