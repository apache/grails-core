package functionaltests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue

@Integration(applicationClass=functionaltests.Application)
class BookFunctionalSpec extends ContainerGebSpec {

    void "Test that when the /viewBooks URL is hit it redirects to the book list"() {
        when:"We go to the book URI"
            go "/book/index"

        then:"Then thew show book view is rendered"
            title == "Book List"
    }    

     void "Test that a book was created in the Bootstrap class"() {
         when:"We go to the book URI"
             go '/book/show/1'

         then:"Then thew show book view is rendered"
            title == "Show Book"
     }

     void "Test that switching language results in correct encodings"() {
         when:"the show page is rendered in german"
             go "/book/show/1?lang=de"
             println driver.pageSource
         then:"The language is correct"
             $('a', class:'create').text() == 'Book anlegen'
             $('input', class:'delete').@value == 'Löschen'
     }

    @Issue('10965')
    void "When creating a book the params are not on the url"() {
        when: 'creating a book'
            go "/book/create"
            $('#title').value('The Stand')
            $('#create').click()

        then:
            title == 'Show Book'
            !currentUrl.contains('title')
            !currentUrl.contains('create')
    }
}
