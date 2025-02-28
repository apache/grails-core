package pubsub.demo

import grails.events.annotation.Publisher
import grails.gorm.services.Service

@Service(Book)
interface BookService {

    @Publisher('newBook')
    Book saveBook(String title)
}