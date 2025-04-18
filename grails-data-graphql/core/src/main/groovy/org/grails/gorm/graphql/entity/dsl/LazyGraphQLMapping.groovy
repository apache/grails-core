package org.grails.gorm.graphql.entity.dsl

import org.grails.gorm.graphql.entity.dsl.helpers.ExecutesClosures

/**
 * A class to lazy initialize GraphQL mappings on
 * GORM entities. This is to allow users to access the
 * mapping context API (for example to specify data fetching
 * instances) inside of the mapping closure without needing
 * the API to be available
 *
 * @author James Kleeh
 * @since 1.0.0
 */
class LazyGraphQLMapping implements ExecutesClosures {

    Closure closure

    protected LazyGraphQLMapping(Closure closure) {
        this.closure = closure
    }

    GraphQLMapping initialize() {
        GraphQLMapping mapping = new GraphQLMapping()
        withDelegate(closure, mapping)
        mapping
    }
}
