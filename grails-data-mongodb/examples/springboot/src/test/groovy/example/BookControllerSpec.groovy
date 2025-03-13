package example

import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * This test relies on a local instance of MongoDB running
 */
class BookControllerSpec extends Specification {

    @Shared @AutoCleanup MongoDatastore datastore = new MongoDatastore(getClass().getPackage())

    BookController bookController = new BookController(bookService: datastore.getService(BookService))


    @Rollback
    void "test find by title"() {
        given:
        def mockMvc = MockMvcBuilders.standaloneSetup(bookController).build()
        Book.DB.drop()
        Book.saveAll(new Book(title: "The Stand"), new Book(title: "It"))
        datastore.currentSession.flush()

        when:
        def response = mockMvc.perform(get("/books/It"))

        then:
        response
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(content().json('{"title":"It","id":2}'))

    }

}
