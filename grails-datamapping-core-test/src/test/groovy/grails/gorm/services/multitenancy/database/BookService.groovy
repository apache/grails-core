package grails.gorm.services.multitenancy.database

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional

@CurrentTenant
@Transactional
class BookService {

    void saveBook(String title) {
        new Book(title: "The Stand").save()
    }

    @ReadOnly
    int countBooks() {
        Book.count()
    }
}
