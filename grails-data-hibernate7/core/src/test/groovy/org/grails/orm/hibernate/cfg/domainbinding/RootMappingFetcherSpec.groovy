package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Mapping
import spock.lang.Specification

class RootMappingFetcherSpec extends Specification {

    RootMappingFetcher fetcher = new RootMappingFetcher()

    void "test getRootMapping returns null for null input"() {
        expect:
        fetcher.getRootMapping(null) == null
    }

    void "test getRootMapping returns null for non-HibernatePersistentEntity"() {
        given:
        PersistentEntity entity = Mock(PersistentEntity)
        entity.getRootEntity() >> entity

        expect:
        fetcher.getRootMapping(entity) == null
    }

    void "test getRootMapping returns mapping for HibernatePersistentEntity"() {
        given:
        HibernatePersistentEntity entity = Mock(HibernatePersistentEntity)
        Mapping mapping = new Mapping()
        entity.getRootEntity() >> entity
        entity.getMappedForm() >> mapping

        expect:
        fetcher.getRootMapping(entity) == mapping
    }

    void "test getRootMapping returns root mapping for child HibernatePersistentEntity"() {
        given:
        HibernatePersistentEntity root = Mock(HibernatePersistentEntity)
        HibernatePersistentEntity child = Mock(HibernatePersistentEntity)
        Mapping rootMapping = new Mapping()
        
        child.getRootEntity() >> root
        root.getMappedForm() >> rootMapping

        expect:
        fetcher.getRootMapping(child) == rootMapping
    }

    void "test getRootMapping returns null if root is not HibernatePersistentEntity"() {
        given:
        HibernatePersistentEntity child = Mock(HibernatePersistentEntity)
        PersistentEntity root = Mock(PersistentEntity)

        child.getRootEntity() >> root

        expect:
        fetcher.getRootMapping(child) == null
    }
}
