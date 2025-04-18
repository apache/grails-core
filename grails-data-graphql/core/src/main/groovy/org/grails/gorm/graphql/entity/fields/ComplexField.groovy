package org.grails.gorm.graphql.entity.fields

import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.ComplexTyped
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * A class used to represent a field that has a custom (complex) type
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class ComplexField extends Field<ComplexField> implements ComplexTyped<ComplexField> {

    String typeName

    ComplexField typeName(String typeName) {
        this.typeName = typeName
        this
    }

    @Override
    GraphQLOutputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        buildCustomType(typeName, typeManager, mappingContext)
    }

    @Override
    GraphQLInputType getInputType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        buildCustomInputType(typeName + 'Input', typeManager, mappingContext, nullable)
    }

    @Override
    void validate() {
    	super.validate()
    	if (typeName == null) {
            throw new IllegalArgumentException('The type name must be specified for fields with a complex type')
        }
        if (fields.empty) {
            throw new IllegalArgumentException('At least 1 field is required for fields with a complex type')
        }
    }
}
