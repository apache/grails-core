package example

import grails.gorm.transactions.ReadOnly
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RestController
class BookController {

    @Autowired
    BookService bookService

    @ReadOnly
    @RequestMapping("/books")
    List<Book> books() {
        Book.list()
    }

    @RequestMapping("/books/{title}")
    List<Book> booksByTitle(@PathVariable('title') String title) {
        bookService.find(title)
    }
}
