package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.FetchMode
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set
import spock.lang.Subject
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder

class CollectionForPropertyConfigBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionForPropertyConfigBinder binder = new CollectionForPropertyConfigBinder()




    @Unroll
    def "should bind lazy settings based on fetch mode '#fetchMode.name()'} and an explicit lazy config of #lazySetting"() {
        given: "A hibernate collection and a mocked property"
        def owner = new RootClass(grailsDomainBinder.metadataBuildingContext)
        def collection = new Set(grailsDomainBinder.metadataBuildingContext, owner)
        def property = Mock(HibernateToManyProperty)

        // Set initial state
        collection.setLazy(false)
        collection.setExtraLazy(false)

        and: "the property is stubbed"
        property.getFetchMode() >> fetchMode
        property.getLazy() >> lazySetting

        when: "the binder is applied"
        binder.bindCollectionForPropertyConfig(collection, property)

        then: "the collection's lazy and extraLazy properties are set according to the binder's logic"
        collection.isLazy() == expectedIsLazy
        collection.isExtraLazy() == expectedIsExtraLazy

        where:
        fetchMode         | lazySetting || expectedIsLazy | expectedIsExtraLazy
        FetchMode.JOIN    | true        || false          | true
        FetchMode.JOIN    | false       || false          | false
        FetchMode.JOIN    | null        || false          | false
        FetchMode.SELECT  | true        || true           | true
        FetchMode.SELECT  | false       || true           | false
        FetchMode.SELECT  | null        || true           | false
//        FetchMode.SUBSELECT | true      || true           | true
    }



}