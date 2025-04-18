package org.grails.gorm.graphql.types

import groovy.transform.CompileStatic

/**
 * An enum to store the base operations provided
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
enum GraphQLOperationType {
    CREATE,
    UPDATE,
    OUTPUT,
    DELETE
}
