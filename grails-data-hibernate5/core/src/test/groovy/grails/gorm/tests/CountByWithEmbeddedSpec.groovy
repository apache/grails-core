package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.GormSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 20/04/16.
 */
class CountByWithEmbeddedSpec extends GormSpec {

    @Issue('https://github.com/grails/grails-core/issues/9846')
    void "Test countBy query with embedded entity"() {
        given:
        new CountByPerson(name: "Fred", bornInCountry: new CountByCountry(name: "England")).save(flush:true)
        new CountByPerson(bornInCountry: new CountByCountry(name: "Scotland")).save(flush:true)
        expect:
        CountByPerson.countByNameIsNotNull() == 1
    }
    @Override
    List getDomainClasses() {
        [CountByPerson]
    }
}
@Entity
class CountByPerson {
    String name
    CountByCountry bornInCountry

    static embedded = ['bornInCountry']

    static constraints = {
        name nullable: true
        bornInCountry nullable: true
    }
}

class CountByCountry {
    String name
}
