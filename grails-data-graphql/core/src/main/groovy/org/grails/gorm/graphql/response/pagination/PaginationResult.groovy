package org.grails.gorm.graphql.response.pagination

/**
 * Stores the result of a pagination query
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface PaginationResult {

    Collection getResults()

    Long getTotalCount()
}
