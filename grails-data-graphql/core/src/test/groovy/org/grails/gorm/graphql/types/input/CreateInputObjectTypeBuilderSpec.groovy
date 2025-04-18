package org.grails.gorm.graphql.types.input

import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import org.grails.gorm.graphql.entity.property.manager.CompositeId
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.entity.property.manager.EmbeddedEntity
import org.grails.gorm.graphql.types.GraphQLPropertyType

class CreateInputObjectTypeBuilderSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [
            CompositeId, EmbeddedEntity
    ] }

    CreateInputObjectTypeBuilder builder

    void setup() {
        builder = new CreateInputObjectTypeBuilder(new DefaultGraphQLDomainPropertyManager(), null)
    }

    void "test type"() {
        expect:
        builder.type == GraphQLPropertyType.CREATE
    }

    void "test property excluded via mapping"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(CompositeId.gormPersistentEntity)

        then:
        !props*.name.contains('bar')
    }

    void "test property added via mapping"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(CompositeId.gormPersistentEntity)

        then:
        props*.name.contains('foo')
    }

    void "test timestamps are excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(EmbeddedEntity.gormPersistentEntity)

        then:
        !props*.name.contains('dateCreated')
        !props*.name.contains('lastUpdated')
    }

    void "test version is excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(EmbeddedEntity.gormPersistentEntity)

        then:
        !props*.name.contains('version')
    }

    void "test id is excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(EmbeddedEntity.gormPersistentEntity)

        then:
        !props*.name.contains('id')
    }

    void "test composite ids are included"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(CompositeId.gormPersistentEntity)

        then:
        props*.name.contains('title')
        props*.name.contains('description')
    }

}
