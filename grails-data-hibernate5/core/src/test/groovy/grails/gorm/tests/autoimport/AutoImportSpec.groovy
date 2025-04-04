package grails.gorm.tests.autoimport

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.GormSpec

class AutoImportSpec extends GormSpec {

    void "test a domain with a getter"() {
        when:
        new A().save(flush:true, validate:false)

        then:
        noExceptionThrown()
    }
    @Override
    List getDomainClasses() {
        [A, grails.gorm.tests.autoimport.other.A]
    }
}

@Entity
class A {

}
