package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback

class IncrementGeneratorSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([EntityWithIncrement])
    }

    @Rollback
    //TODO Still broken
    void "test increment generator"() {
        when:
        def entity1 = new EntityWithIncrement(name: "test1").save(flush: true)
        def entity2 = new EntityWithIncrement(name: "test2").save(flush: true)

        then:
        entity1.id != null
        entity2.id != null
        entity2.id > entity1.id
    }
}

@Entity
class EntityWithIncrement {
    Long id
    String name
    static mapping = {
        id generator: 'increment'
    }
}
