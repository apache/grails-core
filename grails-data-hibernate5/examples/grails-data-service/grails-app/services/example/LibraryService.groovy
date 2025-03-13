package example

import grails.gorm.transactions.Transactional

@Transactional
class LibraryService {

    BookService bookService
    PersonService personService

    @Transactional(readOnly = true)
    Boolean bookExists(Serializable id) {
        assert bookService != null
        bookService.get(id)
    }

    Person addMember(String firstName, String lastName) {
        assert personService != null
        personService.save(firstName, lastName)
    }

}
