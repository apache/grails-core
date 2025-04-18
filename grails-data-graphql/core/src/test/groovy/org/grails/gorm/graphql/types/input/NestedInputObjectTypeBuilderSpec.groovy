package org.grails.gorm.graphql.types.input

import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.custom.Circular
import org.grails.gorm.graphql.domain.general.toone.BelongsToHasOne
import org.grails.gorm.graphql.domain.general.toone.Embed
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import org.grails.gorm.graphql.entity.property.manager.CompositeId
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.entity.property.manager.EmbeddedEntity
import org.grails.gorm.graphql.types.GraphQLPropertyType

class NestedInputObjectTypeBuilderSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [
            BelongsToHasOne, Embed, CompositeId, EmbeddedEntity, Circular
    ] }

    NestedInputObjectTypeBuilder builder

    void setup() {
        builder = new NestedInputObjectTypeBuilder(new DefaultGraphQLDomainPropertyManager(), null, GraphQLPropertyType.UPDATE)
    }
    /**
     * Not attempting this with a composite keyed entity because
     * embedding a composite keyed identity doesn't work in Hibernate
     * GORM
     */
    void "test timestamps and version are excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(EmbeddedEntity.gormPersistentEntity)

        then:
        props*.name == ['id', 'title']
        props[0].nullable
    }

    void "test associations not the owning side are excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(BelongsToHasOne.gormPersistentEntity)

        then: 'one is excluded because it is not the owning side'
        props*.name == ['id']
    }

    void "test circular associations are excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(Circular.gormPersistentEntity)

        then: 'circular properties are excluded'
        props*.name== ['id', 'otherCircular', 'circulars']
    }

}