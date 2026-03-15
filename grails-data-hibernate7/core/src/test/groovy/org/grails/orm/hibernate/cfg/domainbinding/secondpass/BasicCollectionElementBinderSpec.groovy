package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Collection
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set
import org.hibernate.mapping.Table
import spock.lang.Subject

class BasicCollectionElementBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    BasicCollectionElementBinder binder

    // Mock the collaborator
    EnumTypeBinder enumTypeBinder = Mock(EnumTypeBinder)

    void setup() {
        def domainBinder = getGrailsDomainBinder()

        // Inject the mocked enumTypeBinder into the Subject
        binder = new BasicCollectionElementBinder(
                domainBinder.metadataBuildingContext,
                domainBinder.namingStrategy,
                enumTypeBinder,
                new SimpleValueColumnBinder(),
                new SimpleValueColumnFetcher(),
                new ColumnConfigToColumnBinder()
        )
    }

    private Collection collectionWithTable(String tableName) {
        def mbc = getGrailsDomainBinder().metadataBuildingContext
        def collection = new Set(mbc, new RootClass(mbc))
        collection.setCollectionTable(new Table(tableName))
        return collection
    }

    void "bind creates BasicValue with column for scalar collection"() {
        given:
        def entity = createPersistentEntity(BCEBAuthor)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName("tags")
        Collection collection = collectionWithTable("bceb_author_tags")

        property.setCollection(collection)

        when:
        BasicValue element = binder.bind(property)

        then:
        element != null
        element.getColumnSpan() > 0
        // Ensure the enum binder is NOT called for a String collection
        0 * enumTypeBinder.bindEnumTypeForColumn(_, _, _)
    }

    void "bind delegates to enumTypeBinder for enum collection"() {
        given:
        def entity = createPersistentEntity(BCEBAuthor)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName("statuses")
        Collection collection = collectionWithTable("bceb_author_statuses")

        property.setCollection(collection)

        // Create a dummy BasicValue to return from the mock
        def mockValue = new BasicValue(getGrailsDomainBinder().metadataBuildingContext, collection.getCollectionTable())

        when:
        BasicValue element = binder.bind(property)

        then:
        element != null
        // Corrected: Match the 3-argument signature (Property, Class, String)
        1 * enumTypeBinder.bindEnumTypeForColumn(property, BCEBStatus, _ as String) >> mockValue
    }
}

enum BCEBStatus { ACTIVE, INACTIVE }

@Entity
class BCEBAuthor {
    Long id
    java.util.Set<String> tags
    java.util.Set<BCEBStatus> statuses
    static hasMany = [tags: String, statuses: BCEBStatus]
}