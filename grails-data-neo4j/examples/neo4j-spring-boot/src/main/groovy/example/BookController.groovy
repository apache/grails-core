package example

import grails.gorm.transactions.ReadOnly
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class BookController {

    @Autowired
    BookService bookService

    @RequestMapping("/book")
    @ReadOnly
    Book greeting(@RequestParam(required = false) String title) {
        if(title) {
            return bookService.find(title)
        }
        else {
            return Book.first()
        }
    }
}
