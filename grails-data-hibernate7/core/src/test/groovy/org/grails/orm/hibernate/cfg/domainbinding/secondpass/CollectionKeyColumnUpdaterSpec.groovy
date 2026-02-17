package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.hibernate.mapping.Column
import org.hibernate.mapping.DependantValue
import spock.lang.Subject

class CollectionKeyColumnUpdaterSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionKeyColumnUpdater updater = new CollectionKeyColumnUpdater()

    void "test forceNullableAndCheckUpdateable with single unidirectional association"() {
        given:
        def owner = createPersistentEntity(CollectionKeyColumnUpdaterSpecParent)
        def property = (GrailsHibernatePersistentProperty) owner.getPropertyByName("children")
        
        Column column = new Column("test")
        column.setNullable(false)
        
        DependantValue key = Mock(DependantValue)
        key.getColumns() >> [column]

        when:
        updater.forceNullableAndCheckUpdateable(key, property)

        then:
        column.isNullable()
        1 * key.setUpdateable(true)
    }

    void "test forceNullableAndCheckUpdateable with multiple unidirectional associations"() {
        given:
        def owner = createPersistentEntity(CollectionKeyColumnUpdaterSpecMultiParent)
        def property = (GrailsHibernatePersistentProperty) owner.getPropertyByName("children1")
        
        Column column = new Column("test")
        
        DependantValue key = Mock(DependantValue)
        key.getColumns() >> [column]

        when:
        updater.forceNullableAndCheckUpdateable(key, property)

        then:
        1 * key.setUpdateable(false)
    }
}

@Entity
class CollectionKeyColumnUpdaterSpecParent {
    Long id
    static hasMany = [children: CollectionKeyColumnUpdaterSpecChild]
}

@Entity
class CollectionKeyColumnUpdaterSpecChild {
    Long id
}

@Entity
class CollectionKeyColumnUpdaterSpecMultiParent {
    Long id
    static hasMany = [children1: CollectionKeyColumnUpdaterSpecChild, children2: CollectionKeyColumnUpdaterSpecChild]
}
