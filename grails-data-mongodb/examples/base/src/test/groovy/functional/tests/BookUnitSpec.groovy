package functional.tests

import grails.test.mongodb.MongoSpec
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

class BookUnitSpec extends MongoSpec implements EmbeddedMongoClient {

    @Shared
    final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:latest"))

    void "Test low-level API extensions"() {
        when:
        def db = createMongoClient().getDatabase("test")
//        db.drop()
        // Insert a document
        db['languages'].insert([name: 'Groovy'])
        // A less verbose way to do it
        db.languages.insert(name: 'Ruby')
        // Yet another way
        db.languages << [name: 'Python']

        then:
        db.languages.count() == 3
    }

    void "Test GORM access"(){
        when:
        Book book = new Book(title: 'El Quijote').save(flush: true)

        then:
        Book.count() ==1

        when:
        book = Book.findByTitle('El Quijote')

        then:
        book.id
    }

}
