package org.grails.gorm.graphql.entity.property.manager

import grails.gorm.annotation.Entity
import org.codehaus.groovy.util.HashCodeHelper
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.ordering.Ordering
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import spock.lang.Shared

class DefaultGraphQLDomainPropertyManagerSpec extends HibernateSpec {

    @Shared GraphQLDomainPropertyManager manager

    List<Class> getDomainClasses() { [NormalId, CompositeId, EmbeddedEntity, Ordering] }

    void setupSpec() {
        manager = new DefaultGraphQLDomainPropertyManager()
    }

    void "test retrieving domain properties"() {
        when:
        List<GraphQLDomainProperty> properties = manager.builder().getProperties(mappingContext.getPersistentEntity(NormalId.name))

        then:
        //The timestamp, version, identifiers of the embedded properties are ignored
        properties*.name == ['id', 'version', 'embeddedEntity', 'age', 'taxRate', 'price', 'embeddedPogo', 'tax']
        !properties.find { it.name == 'tax' }.input // Derived properties are input false by default
        !properties.find { it.name == 'id' }.nullable
    }

    void "test retrieving domain properties exclude properties"() {
        when:
        List<GraphQLDomainProperty> properties = manager
                .builder()
                .exclude('version', 'age')
                .getProperties(mappingContext.getPersistentEntity(NormalId.name))

        then:
        properties*.name == ['id', 'embeddedEntity', 'taxRate', 'price', 'embeddedPogo', 'tax']
    }

    void "test retrieving domain properties exclude identifers"() {
        when:
        List<GraphQLDomainProperty> properties = manager
                .builder()
                .excludeIdentifiers()
                .getProperties(mappingContext.getPersistentEntity(NormalId.name))

        then:
        properties*.name == ['version', 'embeddedEntity', 'age', 'taxRate', 'price', 'embeddedPogo', 'tax']
    }

    void "test retrieving domain properties exclude version"() {
        when:
        List<GraphQLDomainProperty> properties = manager
                .builder()
                .excludeVersion()
                .getProperties(mappingContext.getPersistentEntity(NormalId.name))

        then:
        properties*.name == ['id', 'embeddedEntity', 'age', 'taxRate', 'price', 'embeddedPogo', 'tax']
    }

    void "test retrieving domain properties always nullable"() {
        when:
        List<GraphQLDomainProperty> properties = manager
                .builder()
                .alwaysNullable()
                .getProperties(mappingContext.getPersistentEntity(NormalId.name))

        then:
        properties*.name == ['id', 'version', 'embeddedEntity', 'age', 'taxRate', 'price', 'embeddedPogo', 'tax']
        properties.findAll { !it.nullable }.empty
    }

    void "test retrieving domain properties with condition"() {
        when:
        List<GraphQLDomainProperty> properties = manager
                .builder()
                .condition { PersistentProperty prop ->
                    prop.name.contains('i')
                }
                .getProperties(mappingContext.getPersistentEntity(NormalId.name))

        then:
        properties*.name == ['id', 'version', 'embeddedEntity', 'price']
    }

    void "test retrieving domain properties with composite id"() {
        when:
        List<GraphQLDomainProperty> properties = manager
                .builder()
                .getProperties(mappingContext.getPersistentEntity(CompositeId.name))

        then: //bar is excluded via the mapping, foo is added
        properties*.name == ['description', 'title', 'dateCreated', 'lastUpdated', 'foo']
    }

    void "test retrieving domain properties excluding timestamps"() {
        when:
        List<GraphQLDomainProperty> properties = manager
                .builder()
                .excludeTimestamps()
                .getProperties(mappingContext.getPersistentEntity(CompositeId.name))

        then: //bar is excluded via the mapping, foo is added
        properties*.name == ['description', 'title', 'foo']
    }
    
    void "test retrieving domain properties obey ordering"(){
        when:
        List<GraphQLDomainProperty> properties = manager
            .builder()
            .getProperties(mappingContext.getPersistentEntity(Ordering.name))

        then:
        properties*.name == [
            'orderNeg',
            'id',
            'version',
            'order0',
            'order1',
            'order1a',
            'order2',
            'orderNullc',
            'orderNulld',
            'order8'
        ]
            
    }

}

@Entity
class NormalId {
    String age
    Embedded embeddedPogo
    EmbeddedEntity embeddedEntity

    Float price
    Float taxRate
    Float tax

    static mapping = {
        tax formula: 'PRICE * TAX_RATE'
    }

    static embedded = ['embeddedEntity', 'embeddedPogo']

    static graphql = true

}

@Entity
class CompositeId implements Serializable {

    String title
    String description

    Long bar

    Date dateCreated
    Date lastUpdated

    int hashCode() {
        int hashCode = HashCodeHelper.initHash()
        if (title) {
            hashCode = HashCodeHelper.updateHash(hashCode, title)
        }
        if (description) {
            hashCode = HashCodeHelper.updateHash(hashCode, description)
        }
        hashCode
    }

    @Override
    boolean equals(Object other) {
        if (other instanceof CompositeId) {
            other.title == title && other.description == description
        }
    }

    static mapping = {
        id composite: ['title', 'description']
        version false
    }

    static graphql = GraphQLMapping.build {
        exclude('bar')
        add('foo', String)
    }
}

class Embedded {
    String name
}

@Entity
class EmbeddedEntity {
    String title

    Date dateCreated
    Date lastUpdated
}
