package org.grails.gorm.graphql.entity.arguments

import graphql.schema.GraphQLInputType
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.ComplexTyped
import org.grails.gorm.graphql.entity.dsl.helpers.ExecutesClosures
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Used to create arguments to custom operations that are a custom (complex) type
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@InheritConstructors
class ComplexArgument extends CustomArgument<ComplexArgument> implements ComplexTyped<ComplexArgument>, ExecutesClosures {

    String typeName

    ComplexArgument typeName(String typeName) {
        this.typeName = typeName
        this
    }

    private ComplexTyped accepts = (ComplexTyped)new Object().withTraits(ComplexTyped).defaultNull(false)

    void accepts(@DelegatesTo(value = ComplexTyped, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        withDelegate(closure, accepts)
    }

    @Override
    GraphQLInputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        accepts.buildCustomInputType(typeName, typeManager, mappingContext, nullable)
    }

    void validate() {
        super.validate()
        if (typeName == null) {
            throw new IllegalArgumentException('The type name must be specified for custom arguments with a complex type')
        }
        if (accepts.fields.empty) {
            throw new IllegalArgumentException('At least 1 field is required for creating a custom argument with a complex type')
        }
    }
}
