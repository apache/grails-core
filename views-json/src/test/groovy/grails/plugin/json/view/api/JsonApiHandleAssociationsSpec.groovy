package grails.plugin.json.view.api

import grails.persistence.Entity
import grails.plugin.json.view.test.JsonRenderResult
import grails.plugin.json.view.test.JsonViewTest
import groovy.json.JsonException
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class JsonApiHandleAssociationsSpec extends Specification implements JsonViewTest, GrailsUnitTest {
    void setup() {
        mappingContext.addPersistentEntities(Author, PublishedBook, Publisher)
    }

    void 'more than one associated objects should produce valid JSON'() {
        given:
            PublishedBook returnOfTheKing = new PublishedBook(
                    title: 'The Return of the King',
                    author: new Author(name: "J.R.R. Tolkien"),
                    publisher: new Publisher(name: 'George Allen & Unwin')
            )
            returnOfTheKing.id = 3
            returnOfTheKing.author.id = 9
            returnOfTheKing.publisher.id = 81


        when:
            JsonRenderResult result = render('''
import grails.plugin.json.view.api.PublishedBook
model {
    PublishedBook book
}

json jsonapi.render(book)
''', [book: returnOfTheKing])

        then: 'should not throw exception'
            notThrown(JsonException)
    }

}


@Entity
class PublishedBook {
    String title
    Author author
    Publisher publisher
}


@Entity
class Publisher {
    String name
}

