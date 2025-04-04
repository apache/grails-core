package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.GormSpec

class RLikeSpec extends GormSpec {

    void "test rlike works with H2"() {
        given:
        new RlikeFoo(name: "ABC").save(flush: true)
        new RlikeFoo(name: "ABCDEF").save(flush: true)
        new RlikeFoo(name: "ABCDEFGHI").save(flush: true)

        when:
        session.clear()
        List<RlikeFoo> allFoos = RlikeFoo.findAllByNameRlike("ABCD.*")

        then:
        allFoos.size() == 2
    }

    @Override
    List getDomainClasses() {
        [RlikeFoo]
    }
}

@Entity
class RlikeFoo {
    String name
}