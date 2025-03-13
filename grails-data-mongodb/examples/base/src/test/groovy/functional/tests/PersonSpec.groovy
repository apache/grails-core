package functional.tests

import grails.test.mongodb.MongoSpec
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

/**
 * Created by graemerocher on 17/10/16.
 */
class PersonSpec extends MongoSpec implements EmbeddedMongoClient {

    @Shared
    final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:latest"))

    void "Test codecs work for custom properties"() {
        when:"An an instance with a custom codec is saved"
        def dob = new Date()
        new Person(name: "Fred", birthday: new Birthday(dob)).save(flush:true)
        Person person = Person.first()

        then:"The result is correct"
        person.name == "Fred"
        person.birthday.date == dob
    }
}
