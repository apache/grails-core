package functional.tests

import com.mongodb.client.MongoClient
import grails.testing.mixin.integration.Integration

import grails.validation.ValidationException
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Created by graemerocher on 12/09/2016.
 */
@Integration(applicationClass = Application)
class BookSpec extends Specification {

    @Autowired
    MongoClient mongoClient

    void "Test low-level API extensions"() {

        when:
        def db = mongoClient.getDatabase("test")
        db.drop()
// Insert a document
        db['languages'].insert([name: 'Groovy'])
// A less verbose way to do it
        db.languages.insert(name: 'Ruby')
// Yet another way
        db.languages << [name: 'Python']

        then:
        db.languages.count() == 3

    }

    void "test fail on error"() {
        when:
        def invalid = new Book(title: "")
        invalid.save()

        then:
        thrown ValidationException
        invalid.hasErrors()
    }
}
