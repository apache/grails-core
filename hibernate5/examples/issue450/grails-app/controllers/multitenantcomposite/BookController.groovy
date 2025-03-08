package multitenantcomposite

import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic

@CompileStatic
class BookController {

    BookService bookService

    def index() {
        [:]
    }

    def grails() {
        render view: 'books',  model: model('grails')
    }
    def groovy() {
        render view: 'books',  model: model('groovy')
    }

    private Map<String, List<Book>> model(String tenantId) {
        [books: Tenants.withId(tenantId) {
            bookService.find()
        }]
    }
}