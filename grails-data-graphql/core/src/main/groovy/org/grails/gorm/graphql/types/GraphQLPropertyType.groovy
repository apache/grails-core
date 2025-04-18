package org.grails.gorm.graphql.types

import groovy.transform.CompileStatic

/**
 * Represents what type of property is being created
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
enum GraphQLPropertyType {

    /**
     * For returning data
     * @see {@link org.grails.gorm.graphql.types.output.ShowObjectTypeBuilder}
     */
    OUTPUT(GraphQLOperationType.OUTPUT, false, false),

    /**
     * For creating data
     * @see {@link org.grails.gorm.graphql.types.input.CreateInputObjectTypeBuilder}
     */
    CREATE(GraphQLOperationType.CREATE, false, false),

    /**
     * For updating data (typically the same as create except nulls allowed)
     * @see {@link org.grails.gorm.graphql.types.input.UpdateInputObjectTypeBuilder}
     */
    UPDATE(GraphQLOperationType.UPDATE, false, false),

    /**
     * For supplying association data during a create
     * @see {@link org.grails.gorm.graphql.types.input.NestedInputObjectTypeBuilder}
     */
    CREATE_NESTED(GraphQLOperationType.CREATE, false, true),

    /**
     * For supplying association data during an update
     * @see {@link org.grails.gorm.graphql.types.input.NestedInputObjectTypeBuilder}
     */
    UPDATE_NESTED(GraphQLOperationType.UPDATE, false, true),

    /**
     * For creating embedded properties
     * @see {@link org.grails.gorm.graphql.types.input.EmbeddedInputObjectTypeBuilder}
     */
    CREATE_EMBEDDED(GraphQLOperationType.CREATE, true, false),

    /**
     * For updating embedded properties
     * @see {@link org.grails.gorm.graphql.types.input.EmbeddedInputObjectTypeBuilder}
     */
    UPDATE_EMBEDDED(GraphQLOperationType.UPDATE, true, false),

    /**
     * For displaying embedded properties
     * @see {@link org.grails.gorm.graphql.types.output.EmbeddedObjectTypeBuilder}
     */
    OUTPUT_EMBEDDED(GraphQLOperationType.OUTPUT, true, false),

    /**
     * For displaying a page of results
     */
    OUTPUT_PAGED(GraphQLOperationType.OUTPUT, false, false)

    final GraphQLOperationType operationType
    final boolean embedded
    final boolean nested

    GraphQLPropertyType(GraphQLOperationType operationType, boolean embedded, boolean nested) {
        this.operationType = operationType
        this.embedded = embedded
        this.nested = nested
    }

    GraphQLPropertyType getEmbeddedType() {
        switch (operationType) {
            case GraphQLOperationType.OUTPUT:
                OUTPUT_EMBEDDED
                break
            case GraphQLOperationType.CREATE:
                CREATE_EMBEDDED
                break
            case GraphQLOperationType.UPDATE:
                UPDATE_EMBEDDED
                break
            default:
                throw new UnsupportedOperationException("No embedded type available for ${operationType.name()}")
        }
    }

    GraphQLPropertyType getNestedType() {
        switch (operationType) {
            case GraphQLOperationType.OUTPUT:
                OUTPUT
                break
            case GraphQLOperationType.CREATE:
                CREATE_NESTED
                break
            case GraphQLOperationType.UPDATE:
                UPDATE_NESTED
                break
            default:
                throw new UnsupportedOperationException("No nested type available for ${operationType.name()}")
        }
    }
}
