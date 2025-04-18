package org.grails.gorm.graphql.types.input

import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.toone.BelongsToHasOne
import org.grails.gorm.graphql.domain.general.toone.Embed
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import org.grails.gorm.graphql.entity.property.manager.CompositeId
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.entity.property.manager.EmbeddedEntity
import org.grails.gorm.graphql.types.GraphQLPropertyType

class EmbeddedInputObjectTypeBuilderSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [
            BelongsToHasOne, Embed, CompositeId, EmbeddedEntity
    ] }

    EmbeddedInputObjectTypeBuilder getBuilder(GraphQLPropertyType type) {
        new EmbeddedInputObjectTypeBuilder(new DefaultGraphQLDomainPropertyManager(), null, type)
    }

    /**
     * Not attempting this with a composite keyed entity because
     * embedding a composite keyed identity doesn't work in Hibernate
     * GORM
     */
    void "test builder is correct UPDATE"() {
        given:
        EmbeddedInputObjectTypeBuilder builder = getBuilder(GraphQLPropertyType.UPDATE)
        List<GraphQLDomainProperty> props

        expect:
        builder.type == GraphQLPropertyType.UPDATE

        when:
        props = builder.builder.getProperties(BelongsToHasOne.gormPersistentEntity)

        then: 'one is excluded because it is not the owning side'
        props.empty

        when:
        props = builder.builder.getProperties(Embed.gormPersistentEntity)

        then: 'one is included because it is the owning side, many is included because it is not bidirectional'
        props*.name == ['many', 'one']
        !props.any { !it.nullable } //all are nullable
    }

    void "test builder is correct CREATE"() {
        given:
        EmbeddedInputObjectTypeBuilder builder = getBuilder(GraphQLPropertyType.CREATE)
        List<GraphQLDomainProperty> props

        expect:
        builder.type == GraphQLPropertyType.CREATE

        when:
        props = builder.builder.getProperties(BelongsToHasOne.gormPersistentEntity)

        then: 'one is excluded because it is not the owning side'
        props.empty

        when:
        props = builder.builder.getProperties(Embed.gormPersistentEntity)

        then: 'one is included because it is the owning side, many is included because it is not bidirectional'
        props*.name == ['many', 'one']
        props.any { !it.nullable } //some are not nullable
    }
}
