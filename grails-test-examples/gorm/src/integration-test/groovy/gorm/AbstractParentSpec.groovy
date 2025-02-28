package gorm

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration(applicationClass = Application)
@Rollback
class AbstractParentSpec extends Specification {

    void "Test that persisting a domain class with an abstract parent works"() {
        when:"An entity with an abstract parent is persisted"
            new ChildA(name:"A", ageA:10).save(flush:true)
            new ChildB(name:"B", ageB:15).save(flush:true)

        then:"The result is correct"
            AbstractParent.count() == 2
    }
}
