package example

import grails.gorm.multitenancy.WithoutTenant
import grails.validation.ValidationException
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver

import static org.springframework.http.HttpStatus.*

class BookController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    BookService bookService

    @WithoutTenant
    def selectTenant(String tenantId) {
        session.setAttribute(SessionTenantResolver.ATTRIBUTE, tenantId)
        flash.message = "Using Tenant $tenantId"
        redirect(controller:"book")
    }

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond bookService.findBooks(params), model:[bookCount: bookService.countBooks()]
    }

    def show(Long id) {
        Book book = bookService.find(id)
        respond book
    }

    def create() {
        respond new Book(params)
    }

    def save(String title) {
        try {
            Book book = bookService.saveBook(title)
            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'book.label', default: 'Book'), book.id])
                    redirect book
                }
                '*' { respond book, [status: CREATED] }
            }
        } catch (ValidationException e) {
            respond e.errors, view:'create'
        }
    }

    def edit(Long id) {
        Book book = bookService.find(id)
        respond book
    }

    def update(Long id, String title) {
        try {
            Book book = bookService.updateBook(id, title)
            if (book == null) {
                notFound()
            }
            else {
                request.withFormat {
                    form multipartForm {
                        flash.message = message(code: 'default.updated.message', args: [message(code: 'book.label', default: 'Book'), book.id])
                        redirect book
                    }
                    '*'{ respond book, [status: OK] }
                }
            }
        } catch (ValidationException e) {
            respond e.errors, view:'edit'
        }
    }

    def delete(Long id) {
        Book book = bookService.deleteBook(id)
        if (book == null) {
            notFound()
            return
        }
        else {
            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'book.label', default: 'Book'), book.id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'book.label', default: 'Book'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
