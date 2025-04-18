package org.grails.gorm.graphql.entity.arguments

import graphql.schema.GraphQLInputType
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.Typed
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Used to create arguments to custom operations that are a simple type
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@InheritConstructors
class SimpleArgument extends CustomArgument<SimpleArgument> implements Typed<SimpleArgument> {

    GraphQLPropertyType propertyType = GraphQLPropertyType.UPDATE

    SimpleArgument propertyType(GraphQLPropertyType propertyType) {
        this.propertyType = propertyType
        this
    }

    @Override
    GraphQLInputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        resolveInputType(typeManager, mappingContext, nullable, propertyType)
    }

    void validate() {
        super.validate()

        if (returnType == null) {
            throw new IllegalArgumentException('A return type is required for creating arguments to custom operations')
        }
    }
}
