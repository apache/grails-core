package org.grails.gorm.graphql.entity.operations

import groovy.transform.CompileStatic

/**
 * Used to determine if a custom operation is for
 * querying or mutating.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
enum OperationType {
    QUERY,
    MUTATION
}
