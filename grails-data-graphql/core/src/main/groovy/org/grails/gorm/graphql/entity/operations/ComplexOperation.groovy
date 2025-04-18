package org.grails.gorm.graphql.entity.operations

import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.ComplexTyped
import org.grails.gorm.graphql.entity.dsl.helpers.ExecutesClosures
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Used to create custom operations with custom (complex) types
 *
 * @author James Kleeh
 * @since 1.0.0
 */

@CompileStatic
class ComplexOperation extends CustomOperation<ComplexOperation> implements ExecutesClosures {

    String typeName

    ComplexOperation typeName(String typeName) {
        this.typeName = typeName
        this
    }

    private ComplexTyped returns = new Object().withTraits(ComplexTyped)

    void returns(@DelegatesTo(value = ComplexTyped, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        withDelegate(closure, returns)
    }

    @Override
    protected GraphQLOutputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        returns.buildCustomType(typeName, typeManager, mappingContext)
    }

    void validate() {
        super.validate()
        if (typeName == null) {
            throw new IllegalArgumentException('The type name must be specified for custom operations with a complex type')
        }
        if (returns.fields.empty) {
            throw new IllegalArgumentException('At least 1 field is required for creating a custom operation with a complex type')
        }
    }
}
