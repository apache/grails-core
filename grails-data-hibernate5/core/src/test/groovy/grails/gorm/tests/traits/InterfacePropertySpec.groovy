package grails.gorm.tests.traits

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.GormSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 29/05/2017.
 */
class InterfacePropertySpec extends GormSpec {

    @Issue('https://github.com/grails/gorm-hibernate5/issues/38')
    void "test interface that exposes id"() {
        when:
        TestDomain td = new TestDomain(name: "Fred").save(flush:true)

        then:
        td.id
        TestDomain.first().id
    }

    @Override
    List getDomainClasses() {
        [TestDomain]
    }
}

interface ObjectId<T> extends Serializable {
    T getId()
    void setId(T id)
}

@Entity
class TestDomain implements ObjectId<Long> {

    Long id
    String name

    static constraints = {
    }
}
