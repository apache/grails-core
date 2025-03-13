package example

import grails.gorm.services.Service
import grails.gorm.transactions.ReadOnly

@Service(Book)
abstract class BookService {

    @ReadOnly
    abstract Book findByTitle(String title)

    @ReadOnly
    List<Book> findAll() {
        Book.getAll()
    }
}
