package org.grails.gorm.graphql.response.pagination

import grails.gorm.PagedResultList
import groovy.transform.CompileStatic

/**
 * A default pagination response that gathers data
 * from a {@link PagedResultList}
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class PagedResultListPaginationResponse implements PaginationResult {

    private PagedResultList resultList

    PagedResultListPaginationResponse(PagedResultList resultList) {
        this.resultList = resultList
    }

    @Override
    Collection getResults() {
        resultList
    }

    @Override
    Long getTotalCount() {
        resultList.totalCount.longValue()
    }
}
