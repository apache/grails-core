package grails.gorm.services.multitenancy.database

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service

@CurrentTenant
@Service(Book)
interface IBookService {

    Book saveBook(String title)

    Integer countBooks()
}
