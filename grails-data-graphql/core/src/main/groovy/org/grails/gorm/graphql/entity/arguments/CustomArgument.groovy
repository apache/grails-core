package org.grails.gorm.graphql.entity.arguments

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.Defaultable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable
import org.grails.gorm.graphql.entity.dsl.helpers.Named
import org.grails.gorm.graphql.entity.dsl.helpers.Nullable
import org.grails.gorm.graphql.types.GraphQLTypeManager

import static graphql.schema.GraphQLArgument.newArgument

/**
 * Describes an argument to a custom operation
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
abstract class CustomArgument<T> implements Named<T>, Describable<T>, Nullable<T>, Defaultable<T> {

    CustomArgument() {
        nullable = false
    }

    abstract GraphQLInputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext)

    GraphQLArgument.Builder getArgument(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        GraphQLInputType type = getType(typeManager, mappingContext)

        newArgument()
            .name(name)
            .description(description)
            .defaultValue(defaultValue)
            .type(type)
    }

    void validate() {
        if (name == null) {
            throw new IllegalArgumentException('A name is required for creating custom operations')
        }
    }
}
