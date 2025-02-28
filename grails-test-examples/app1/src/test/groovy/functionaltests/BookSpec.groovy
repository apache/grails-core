package functionaltests

import grails.testing.gorm.DomainUnitTest
import spock.lang.Issue
import spock.lang.Specification

/**
 */
class BookSpec extends Specification implements DomainUnitTest<Book> {

    void "Test validating a book"() {
        expect:"The book validates"
            new Book(title:"The Stand").validate()
    }

    @Issue('grails/grails-core#10079')
    void 'Test that auto-timestamp properties are excluded from mass property binding'() {
        given:
        def book = new Book()

        when:
        book.properties = [title: 'Some Title',
                           dateCreated: 'some value',
                           lastUpdated: 'some value']

        then:
        !book.hasErrors()
        book.title == 'Some Title'
        book.dateCreated == null
        book.lastUpdated == null
    }
}
