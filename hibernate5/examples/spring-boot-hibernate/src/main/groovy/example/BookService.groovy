package example

import grails.gorm.services.Service

@Service(Book)
interface BookService {
    List<Book> find(String title)
}