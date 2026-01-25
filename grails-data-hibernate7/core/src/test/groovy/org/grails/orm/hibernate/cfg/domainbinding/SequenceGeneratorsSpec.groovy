package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import spock.lang.Unroll

class SequenceGeneratorsSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([EntityWithIdentity,
                                     EntityWithNative,
                                     EntityWithSequence,
                                     EntityWithTable,
                                     EntityWithUUID,
                                     EntityWithAssigned])
    }


    @Rollback
    void "test identity generator"() {
        when:
        def entity = new EntityWithIdentity(name: "test").save(flush: true)

        then:
        entity.id != null
    }

    @Rollback
    void "test native generator"() {
        when:
        def entity = new EntityWithNative(name: "test").save(flush: true)

        then:
        entity.id != null
    }

    @Rollback
    void "test sequence generator"() {
        when:
        def entity = new EntityWithSequence(name: "test").save(flush: true)

        then:
        entity.id != null
    }

    @Rollback
    void "test table generator"() {
        when:
        def entity = new EntityWithTable(name: "test").save(flush: true)

        then:
        entity.id != null
    }

    @Rollback
    void "test uuid generator"() {
        when:
        def entity = new EntityWithUUID(name: "test").save(flush: true)

        then:
        entity.id != null
        entity.id instanceof String
    }

    @Rollback
    void "test assigned generator"() {
        when:
        def entity = new EntityWithAssigned(id: 123, name: "test").save(flush: true)

        then:
        entity.id == 123
    }
}

@Entity
class EntityWithIdentity {
    Long id
    String name
    static mapping = {
        id generator: 'identity'
    }
}

@Entity
class EntityWithNative {
    Long id
    String name
    static mapping = {
        id generator: 'native'
    }
}

@Entity
class EntityWithSequence {
    Long id
    String name
    static mapping = {
        id generator: 'sequence', params: [sequence_name: 'seq_test']
    }
}

@Entity
class EntityWithTable {
    Long id
    String name
    static mapping = {
        id generator: 'table'
    }
}

@Entity
class EntityWithUUID {
    String id
    String name
    static mapping = {
        id generator: 'uuid'
    }
}

@Entity
class EntityWithAssigned {
    Long id
    String name
    static mapping = {
        id generator: 'assigned'
    }
}