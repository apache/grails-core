package grails.gorm.tests

/**
 * @author graemerocher
 */
class AttachMethodSpec extends GormDatastoreSpec {

    void "Test attach method"() {
        given:
            def test = new Person(firstName:"Bob", lastName:"Builder").save()

        when:
            session.flush()

        then:
            session.contains(test) == true
            test.isAttached()
            test.attached

        when:
            test.discard()

        then:
            !session.contains(test)
            !test.isAttached()
            !test.attached

        when:
            test.attach()

        then:
            session.contains(test)
            test.isAttached()
            test.attached

        when:
            test.discard()

        then:
            test == test.attach()
    }
}
