package example

import grails.gorm.services.Service

@Service(Book)
interface BookService {

    Book get(Serializable id)
}
