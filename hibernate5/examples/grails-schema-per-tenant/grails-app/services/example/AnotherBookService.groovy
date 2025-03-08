package example

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional

/**
 * Created by graemerocher on 06/04/2017.
 */
@CurrentTenant
@Transactional
class AnotherBookService {
    Book saveBook(String title = 'The Stand') {
        new Book(title: title).save()
    }

    @ReadOnly
    int countBooks() {
        Book.count()
    }
}
