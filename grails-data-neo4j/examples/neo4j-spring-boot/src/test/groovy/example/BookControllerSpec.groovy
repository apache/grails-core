package example

import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class BookControllerSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(getClass().getPackage())

    BookController bookController = new BookController(bookService: datastore.getService(BookService))


    @Rollback
    void "test find by title"() {
        given:
        def mockMvc = MockMvcBuilders.standaloneSetup(bookController).build()
        Book.saveAll(
            new Book(title: "The Stand"),
            new Book(title: "It")
        )

        when:
        def response = mockMvc.perform(get("/book?title=It"))

        then:
        response
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(content().json('{"title":"It","id":1}'))

    }

}
