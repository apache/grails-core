package functional.tests

import grails.gorm.services.Service

/**
 * Created by graemerocher on 10/02/2017.
 */
@Service(Book)
interface BookService {

    Book getBook(Serializable id)
}