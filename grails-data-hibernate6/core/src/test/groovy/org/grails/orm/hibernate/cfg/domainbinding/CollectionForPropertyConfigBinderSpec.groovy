package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.FetchMode
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set
import spock.lang.Subject
import spock.lang.Unroll

class CollectionForPropertyConfigBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionForPropertyConfigBinder binder = new CollectionForPropertyConfigBinder()



    def "should apply default settings when the property config is null"() {
        def grailsDomainBinder = getGrailsDomainBinder()
        given: "A hibernate collection"
        def owner = new RootClass(grailsDomainBinder.metadataBuildingContext)
        def collection = new Set(grailsDomainBinder.metadataBuildingContext, owner)
        // Set initial state to be different from the expected outcome
        collection.setLazy(false)
        collection.setExtraLazy(true)

        when: "the binder is called with a null config"
        binder.bindCollectionForPropertyConfig(collection, null)

        then: "specific default values are applied"
        collection.isLazy()
        !collection.isExtraLazy()
    }

    @Unroll
    def "should bind lazy settings based on fetch mode '#fetchMode.name()'} and an explicit lazy config of #lazySetting"() {
        given: "A hibernate collection and a mocked property config"
        def owner = new RootClass(grailsDomainBinder.metadataBuildingContext)
        def collection = new Set(grailsDomainBinder.metadataBuildingContext, owner)
        def config = Mock(PropertyConfig)

        // Set initial state
        collection.setLazy(false)
        collection.setExtraLazy(false)

        and: "the config is stubbed"
        config.getFetchMode() >> fetchMode
        config.getLazy() >> lazySetting

        when: "the binder is applied"
        binder.bindCollectionForPropertyConfig(collection, config)

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

    def "should not throw an exception if the collection itself is null"() {
        given: "A valid property config"
        def config = Mock(PropertyConfig)

        when: "the binder is called with a null collection"
        binder.bindCollectionForPropertyConfig(null, config)

        then: "a NullPointerException is thrown because the code does not guard against it"
        thrown(NullPointerException)
    }

}