package org.grails.gorm.graphql.types.input

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.gorm.graphql.types.GraphQLOperationType
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.entity.property.manager.GraphQLDomainPropertyManager
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * The class used to define which properties are available
 * when providing an embedded object
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class EmbeddedInputObjectTypeBuilder extends AbstractInputObjectTypeBuilder {

    EmbeddedInputObjectTypeBuilder(GraphQLDomainPropertyManager propertyManager, GraphQLTypeManager typeManager, GraphQLPropertyType type) {
        super(propertyManager, typeManager)
        this.type = type
    }

    GraphQLDomainPropertyManager.Builder builder
    GraphQLPropertyType type

    {
        builder = propertyManager.builder()
                .excludeTimestamps()
                .excludeVersion()
                .excludeIdentifiers()
                .condition { PersistentProperty prop ->
                    if (prop instanceof Association) {
                        Association association = (Association)prop
                        boolean owningSide
                        if (association instanceof ManyToOne) {
                            owningSide = false
                        } else {
                            owningSide = association.owningSide
                        }
                        owningSide || !association.bidirectional
                    } else {
                        true
                    }
                }

        if (type.operationType == GraphQLOperationType.UPDATE) {
            builder.alwaysNullable()
        }
    }

}
