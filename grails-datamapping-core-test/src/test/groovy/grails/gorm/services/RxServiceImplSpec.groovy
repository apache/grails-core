package grails.gorm.services

//import grails.gorm.annotation.Entity
//import org.grails.datastore.gorm.services.Implemented
//import org.grails.datastore.gorm.services.implementers.DeleteImplementer
//import org.grails.datastore.mapping.simple.SimpleMapDatastore
//import rx.Single
//import spock.lang.AutoCleanup
//import spock.lang.PendingFeature
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Created by graemerocher on 15/02/2017.
 */
@Ignore
class RxServiceImplSpec extends Specification {
//    @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
//        Book
//    )
//
//    @PendingFeature(reason="bookService.countByTitleLike(The%).toBlocking().value() == 1")
//    void "test find method that returns an observable"() {
//        given:
//        new Book(title: "The Stand").save(flush:true)
//        BookService bookService = datastore.getService(BookService)
//
//        expect:"the observable returns the correct res"
//        bookService.countByTitleLike("The%").toBlocking().value() == 1
//        bookService.countFor("The Stand").toBlocking().value() == 1
//        bookService.count("The Stand").toBlocking().value() == 1
//        bookService.find("The Stand").toList().toBlocking().single().size() == 1
//        bookService.findOne("The Stand").toBlocking().value().title == "The Stand"
//        bookService.findByTitleLike("The%").toBlocking().first().title == "The Stand"
//    }
//
//    @PendingFeature(reason="org.codehaus.groovy.runtime.typehandling.GroovyCastException: Cannot cast object '1' with class 'java.lang.Long' to class 'rx.Single'")
//    void "test delete method"() {
//        given:
//        new Book(title: "The Stand").save(flush:true)
//        new Book(title: "The Shining").save(flush:true)
//        BookService bookService = datastore.getService(BookService)
//
//
//        when:
//        def result = bookService.delete("The Stand").toBlocking().value()
//        def implementer = bookService.getClass().getMethod("delete", String).getAnnotation(Implemented).by()
//        then:
//        implementer == DeleteImplementer
//        result == 1
//
//        when:
//        bookService.find("The Stand").toBlocking().first() == null
//
//        then:
//        thrown(NoSuchElementException)
//
//    }
//
//    @PendingFeature(reason="org.codehaus.groovy.runtime.typehandling.GroovyCastException: Cannot cast object 'grails.gorm.services.Book : 1' with class 'grails.gorm.services.Book' to class 'rx.Single'")
//    void "test find and delete method"() {
//        given:
//        new Book(title: "The Stand").save(flush:true)
//        new Book(title: "The Shining").save(flush:true)
//        BookService bookService = datastore.getService(BookService)
//
//        when:
//        Book result = bookService.deleteOne("The Stand").toBlocking().value()
//
//        then:
//        result != null
//        result.title == "The Stand"
//
//        when:
//        bookService.find("The Stand").toBlocking().first() == null
//
//        then:
//        thrown(NoSuchElementException)
//
//    }
//
//    @PendingFeature(reason="Expected exception of type 'java.lang.UnsupportedOperationException', but got 'groovy.lang.MissingMethodException'")
//    void "test find with string query method"() {
//        given:
//        new Book(title: "The Stand").save(flush:true)
//        new Book(title: "The Shining").save(flush:true)
//        BookService bookService = datastore.getService(BookService)
//
//        when:
//        def result = bookService.findWithQuery("The Stand").toBlocking().first()
//
//        then:
//        thrown(UnsupportedOperationException)
//
//        when:
//        bookService.updateBook("The Stand", "It").toBlocking().first()
//
//        then:
//        thrown(UnsupportedOperationException)
//
//    }
//
//    @PendingFeature(reason="org.codehaus.groovy.runtime.typehandling.GroovyCastException: Cannot cast object '[grails.gorm.services.Book : 2]' with class 'java.util.ArrayList' to class 'rx.Observable' due to: groovy.lang.GroovyRuntimeException: Could not find matching constructor for: rx.Observable(grails.gorm.services.Book)")
//    void "test find with where query method"() {
//        given:
//        new Book(title: "The Stand").save(flush:true)
//        new Book(title: "The Shining").save(flush:true)
//        BookService bookService = datastore.getService(BookService)
//
//        when:
//        Book result = bookService.findWhereTitle("The Shining").toBlocking().first()
//
//        then:
//        result != null
//        result.title == "The Shining"
//
//    }
//
//    @PendingFeature(reason="org.codehaus.groovy.runtime.typehandling.GroovyCastException: Cannot cast object 'grails.gorm.services.Book : 1' with class 'grails.gorm.services.Book' to class 'rx.Single'")
//    void "test save method"() {
//        given:
//        BookService bookService = datastore.getService(BookService)
//
//        when:
//        Book savedBook = bookService.saveBook("The Shining").toBlocking().value()
//        Book result = bookService.findWhereTitle("The Shining").toBlocking().first()
//
//
//        then:
//        savedBook != null
//        savedBook.title == "The Shining"
//        result != null
//        result.title == "The Shining"
//
//        when:
//        savedBook = bookService.updateBook(result.id, "The Stand").toBlocking().value()
//        result = bookService.findWhereTitle("The Stand").toBlocking().first()
//
//        then:
//        savedBook != null
//        savedBook.title == "The Stand"
//        result != null
//        result.title == "The Stand"
//
//        when:
//        bookService.findWhereTitle("The Shining").toBlocking().first()
//
//        then:
//        thrown(NoSuchElementException)
//
//    }
//
//    @PendingFeature(reason="groovy.lang.MissingMethodException: No signature of method: grails.gorm.services.BookServiceImplementation.findBookAuthor() is applicable for argument types: (String) values: [The Stand]")
//    void "test simple projection"() {
//
//        given:
//        new Book(title: "Along Came a Spider", author: "James Patterson").save(flush:true)
//        new Book(title: "The Stand", author: "Stephen King").save(flush:true)
//        BookService bookService = datastore.getService(BookService)
//
//        when:
//        String author = bookService.findBookAuthor("The Stand").toBlocking().value()
//
//        then:
//        author == "Stephen King"
//    }
}
//
//@Entity
//class Book {
//    String title
//    String author
//}
//
//
//@Service(value = Book)
//interface BookService {
//
////    Cannot implement method for argument [title]. No property exists on domain class [java.lang.String]
//    //Single<String> findBookAuthor(String title)
//
////    No implementations possible for method 'rx.Observable updateBook(java.lang.String, java.lang.String)'. Please use an abstract class instead and provide an implementation.
//    //@Query("update ${Book b} set $b.title = $title where $b.title = $oldTitle")
//
////    No implementations possible for method 'rx.Observable updateBook(java.lang.String, java.lang.String)'. Please use an abstract class instead and provide an implementation.
//    //rx.Observable<Number> updateBook(String oldTitle, String title)
//
//    Single<Book> updateBook(Serializable id, String title)
//
//    Single<Book> saveBook(String title)
//
//    @Where({ title ==~ pattern})
//    rx.Observable<Book> findWhereTitle(String pattern)
//
//    @Query("from ${Book b} where $b.title = $title")
//    rx.Observable<Book> findWithQuery(String title)
//
//    Single<Book> deleteOne(String title)
//
//    Single<Number> delete(String title)
//
//    Single<Number> count(String title)
//
//    Single<Number> countByTitleLike(String pattern)
//
//    @Where({ title == title})
//    Single<Number> countFor(String title)
//
//    rx.Observable<Book> find(String title)
//
//    rx.Observable<Book> findByTitleLike(String pattern)
//
//    Single<Book> findOne(String title)
//}

