package org.grails.gorm.graphql.response.pagination

import groovy.transform.CompileStatic

/**
 * Helper class to inform the type system that a custom operation
 * returns a paginated result for the given type.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class PaginatedType {

    Class type
}
