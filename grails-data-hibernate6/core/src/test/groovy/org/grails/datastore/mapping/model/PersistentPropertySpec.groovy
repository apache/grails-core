package org.grails.datastore.mapping.model

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

@Issue('https://github.com/grails/grails-data-mapping/issues/1299')
class PersistentPropertySpec extends HibernateGormDatastoreSpec {

    void "test isUnidirectionalOneToMany"() {
        when:
        def p = createPersistentEntity(Unidirectional).getPropertyByName("foos")

        then:
        p.isUnidirectionalOneToMany()

        when:
        p = createPersistentEntity(BidirectionalParent).getPropertyByName("bars")

        then:
        !p.isUnidirectionalOneToMany()

        when:
        p = createPersistentEntity(Unidirectional).getPropertyByName("name")

        then:
        !p.isUnidirectionalOneToMany()
    }

    void "test isLazyAble"() {
        when:
        def p = createPersistentEntity(Unidirectional).getPropertyByName("foos")

        then:
        !p.isLazyAble()

        when:
        p = createPersistentEntity(BidirectionalChild).getPropertyByName("bar")

        then:
        p.isLazyAble()

        when:
        p = createPersistentEntity(Unidirectional).getPropertyByName("name")

        then:
        p.isLazyAble()
    }

}

@Entity
class Unidirectional {
    String name
    static hasMany = [foos: UnidirectionalChild]
}

@Entity
class UnidirectionalChild {
    String name
}

@Entity
class BidirectionalParent {
    String name
    static hasMany = [bars: BidirectionalChild]
}

@Entity
class BidirectionalChild {
    String name
    static belongsTo = [bar: BidirectionalParent]
}
