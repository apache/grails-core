package example

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service

/**
 * Created by graemerocher on 16/02/2017.
 */
@Service(Book)
@CurrentTenant
interface BookService {

    Book find(Serializable id)

    List<Book> findBooks(Map args)

    Number countBooks()

    Book saveBook(String title)

    Book updateBook(Serializable id, String title)

    Book deleteBook(Serializable id)
}