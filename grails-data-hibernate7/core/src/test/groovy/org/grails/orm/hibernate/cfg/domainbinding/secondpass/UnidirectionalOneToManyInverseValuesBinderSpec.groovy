package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.FetchMode
import org.hibernate.mapping.ManyToOne
import spock.lang.Subject

class UnidirectionalOneToManyInverseValuesBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    UnidirectionalOneToManyInverseValuesBinder binder = new UnidirectionalOneToManyInverseValuesBinder()

    void "test bindUnidirectionalOneToManyInverseValues"() {
        given:
        createPersistentEntity(UOTMBook)
        PersistentEntity authorEntity = createPersistentEntity(UOTMAuthor)
        HibernateToManyProperty property = (HibernateToManyProperty) authorEntity.getPropertyByName("books")
        
        ManyToOne manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        when:
        binder.bindUnidirectionalOneToManyInverseValues(property, manyToOne)

        then:
        manyToOne.isIgnoreNotFound() == false
        manyToOne.isLazy() == true
        manyToOne.getReferencedEntityName() == UOTMBook.name
    }

    void "test bindUnidirectionalOneToManyInverseValues with custom config"() {
        given:
        createPersistentEntity(UOTMBook)
        PersistentEntity authorEntity = createPersistentEntity(UOTMAuthorCustom)
        HibernateToManyProperty property = (HibernateToManyProperty) authorEntity.getPropertyByName("books")

        ManyToOne manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        when:
        binder.bindUnidirectionalOneToManyInverseValues(property, manyToOne)

        then:
        manyToOne.isIgnoreNotFound() == true
        manyToOne.isLazy() == false
        manyToOne.getReferencedEntityName() == UOTMBook.name
    }
}

@Entity
class UOTMBook {
    Long id
    String title
}

@Entity
class UOTMAuthor {
    Long id
    String name
    Set<UOTMBook> books
    static hasMany = [books: UOTMBook]
}

@Entity
class UOTMAuthorCustom {
    Long id
    String name
    Set<UOTMBook> books
    static hasMany = [books: UOTMBook]
    static mapping = {
        books ignoreNotFound: true, fetch: 'join', lazy: false
    }
}
