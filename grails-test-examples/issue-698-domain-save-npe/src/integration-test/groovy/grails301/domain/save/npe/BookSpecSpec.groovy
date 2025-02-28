package grails301.domain.save.npe

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration(applicationClass = Application)
@Rollback
class BookSpecSpec extends Specification {

    void "It works well if there is no validate error in both Java 7 and 8"() {
        given:
        def b = new Book(title: "OK")

        when:
        b.save()

        then:
        Book.count() == 1
    }

    void "It works well if there is a validate error: maxSize"() {
        given:
        def b = new Book(title: "TOO_LONG")

        when:
        b.save()

        then:
        b.hasErrors() // But NPE occurs in Java 7!
    }

    void "It works well if there is a validate error: nullable"() {
        given:
        def b = new Book()

        when:
        b.save()

        then:
        b.hasErrors() // But NPE occurs in Java 7!
    }
}
