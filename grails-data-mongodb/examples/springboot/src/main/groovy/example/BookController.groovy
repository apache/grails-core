package example

import grails.gorm.transactions.ReadOnly
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@CompileStatic
class BookController {

    @Autowired
    BookService bookService

    @RequestMapping("/books")
    @ReadOnly
    List<Book> books() {
        Book.list()
    }

    @RequestMapping("/books/{title}")
    Book booksByTitle(@PathVariable('title') String title) {
        bookService.find(title)
    }
}
