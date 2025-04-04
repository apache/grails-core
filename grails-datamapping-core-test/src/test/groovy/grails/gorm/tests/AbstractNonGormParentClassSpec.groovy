package grails.gorm.tests

import grails.persistence.Entity

/**
 * Created by graemerocher on 25/02/16.
 */
class AbstractNonGormParentClassSpec extends GormDatastoreSpec {

    void "Test a concrete domain class that extends a common base class"() {
        expect:
        session.mappingContext.getPersistentEntity(ConcreteFoo.name)
        !session.mappingContext.getPersistentEntity(Common.name)

    }
    @Override
    List getDomainClasses() {
        [ConcreteFoo]
    }
}

abstract class Common {
    String foo
}

@Entity
class ConcreteFoo extends Common {
    String bar
}
