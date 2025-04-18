package org.grails.gorm.graphql.plugin.binding

import org.grails.gorm.graphql.binding.GraphQLDataBinder

/**
 * A default data binder using Grails data binding
 *
 * @author James Kleeh
 */
class GrailsGraphQLDataBinder implements GraphQLDataBinder {

    @Override
    void bind(Object object, Map data) {
        object.properties = data
    }
}
