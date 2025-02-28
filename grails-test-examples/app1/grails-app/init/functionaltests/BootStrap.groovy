package functionaltests

import jakarta.servlet.ServletContext

class BootStrap {

    ServletContext servletContext

    def init = {
        Book.withTransaction {
            new Book(title:"Example Book").save(flush:true)
            assert Book.count() == 1
        }
    }

    def destroy = {
    }
}
