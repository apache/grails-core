package functional.tests

import geb.spock.*
import grails.testing.mixin.integration.Integration
import spock.lang.Ignore

@Integration(applicationClass = Application)
class BookControllerSpec extends GebSpec {

    void "Test list books"() {
        when:"The home page is visited"
            go '/book/index'

        then:"The title is correct"
        	title == "Book List"
    }

    @Ignore
    void "Test save book"() {
        when:
        go "/book/create"
        $('form').title = "The Stand"
        $('input.save').click()

        then:"The book is correct"
        title == "Show Book"
        $('li.fieldcontain div').text() == 'The Stand'
    }
}
