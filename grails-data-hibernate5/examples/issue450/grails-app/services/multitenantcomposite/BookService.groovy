package multitenantcomposite

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service

@CurrentTenant
@Service(Book)
interface BookService {
    Book save(String id, String title)
    List<Book> find()
}