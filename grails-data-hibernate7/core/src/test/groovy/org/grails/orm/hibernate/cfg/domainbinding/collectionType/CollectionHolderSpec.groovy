package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import grails.gorm.specs.HibernateGormDatastoreSpec
import spock.lang.Subject
import spock.lang.Unroll

class CollectionHolderSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionHolder holder

    def setup() {
        holder = new CollectionHolder(getGrailsDomainBinder().getMetadataBuildingContext())
    }

    @Unroll
    def "should return correct collection type for #collectionClass"() {
        expect:
        holder.get(collectionClass)?.getClass() == expectedType

        where:
        collectionClass | expectedType
        Set             | SetCollectionType
        SortedSet       | SetCollectionType
        List            | ListCollectionType
        Collection      | BagCollectionType
        Map             | MapCollectionType
    }

    def "should return null for unsupported type"() {
        expect:
        holder.get(String) == null
    }
}
