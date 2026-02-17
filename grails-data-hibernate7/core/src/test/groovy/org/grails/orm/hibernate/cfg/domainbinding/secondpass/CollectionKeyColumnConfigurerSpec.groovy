package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.hibernate.mapping.Column
import org.hibernate.mapping.DependantValue
import spock.lang.Subject

class CollectionKeyColumnConfigurerSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionKeyColumnConfigurer configurer = new CollectionKeyColumnConfigurer()

    void "test configure with single unidirectional association"() {
        given:
        def owner = createPersistentEntity(CollectionKeyColumnConfigurerSpecParent)
        def property = (GrailsHibernatePersistentProperty) owner.getPropertyByName("children")
        
        Column column1 = new Column("keyCol1")
        Column column2 = new Column("keyCol2")
        
        DependantValue key = Mock(DependantValue)
        key.getColumns() >> [column1, column2]

        when:
        configurer.configure(key, property)

        then:
        column1.isNullable()
        column2.isNullable()
        1 * key.setUpdateable(true)
    }

    void "test configure with multiple unidirectional associations"() {
        given:
        def owner = createPersistentEntity(CollectionKeyColumnConfigurerSpecMultiParent)
        def property = (GrailsHibernatePersistentProperty) owner.getPropertyByName("children1")
        
        Column column1 = new Column("keyCol1")
        
        DependantValue key = Mock(DependantValue)
        key.getColumns() >> [column1]

        when:
        configurer.configure(key, property)

        then:
        column1.isNullable()
        1 * key.setUpdateable(false)
    }

    void "test configure with bidirectional association"() {
        given:
        def owner = createPersistentEntity(CollectionKeyColumnConfigurerSpecBiParent)
        def property = (GrailsHibernatePersistentProperty) owner.getPropertyByName("children")
        
        Column column = new Column("keyCol")
        
        DependantValue key = Mock(DependantValue)
        key.getColumns() >> [column]

        when:
        configurer.configure(key, property)

        then:
        column.isNullable()
        1 * key.setUpdateable(true)
    }
}

@Entity
class CollectionKeyColumnConfigurerSpecParent {
    Long id
    static hasMany = [children: CollectionKeyColumnConfigurerSpecChild]
}

@Entity
class CollectionKeyColumnConfigurerSpecChild {
    Long id
}

@Entity
class CollectionKeyColumnConfigurerSpecMultiParent {
    Long id
    static hasMany = [children1: CollectionKeyColumnConfigurerSpecChild, children2: CollectionKeyColumnConfigurerSpecChild]
}

@Entity
class CollectionKeyColumnConfigurerSpecBiParent {
    Long id
    static hasMany = [children: CollectionKeyColumnConfigurerSpecBiChild]
}

@Entity
class CollectionKeyColumnConfigurerSpecBiChild {
    Long id
    CollectionKeyColumnConfigurerSpecBiParent parent
    static belongsTo = [parent: CollectionKeyColumnConfigurerSpecBiParent]
}
