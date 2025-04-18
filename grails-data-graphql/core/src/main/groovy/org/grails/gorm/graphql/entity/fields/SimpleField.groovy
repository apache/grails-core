package org.grails.gorm.graphql.entity.fields

import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.entity.dsl.helpers.Typed
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * A field with a simple type. {@see Field}
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class SimpleField extends Field<SimpleField> implements Typed<SimpleField> {

	GraphQLPropertyType propertyType = GraphQLPropertyType.UPDATE

	SimpleField propertyType(GraphQLPropertyType propertyType) {
		this.propertyType = propertyType
		this
	}

	GraphQLOutputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
		resolveOutputType(typeManager, mappingContext)
	}

	@Override
	GraphQLInputType getInputType(GraphQLTypeManager typeManager, MappingContext mappingContext) {
		resolveInputType(typeManager, mappingContext, nullable, propertyType)
	}

	void validate() {
		super.validate()

		if (returnType == null) {
            throw new IllegalArgumentException('A return type is required for creating fields')
        }
	}
}
