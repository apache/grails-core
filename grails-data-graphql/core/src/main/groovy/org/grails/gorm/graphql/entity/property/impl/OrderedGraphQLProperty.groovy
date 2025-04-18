package org.grails.gorm.graphql.entity.property.impl

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty

/**
 * A class to extend from to support the default sorting mechanism
 * for GraphQL properties
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
abstract class OrderedGraphQLProperty implements GraphQLDomainProperty, Comparable<OrderedGraphQLProperty> {

    abstract Integer getOrder()

    @Override
    int compareTo(OrderedGraphQLProperty o) {
        if (order != null) {
            if (o.order == null) {
                -1
            }
            else {
                order <=> o.order ?: name <=> o.name
            }
        }
        else {
            if (o.order != null) {
                1
            }
            else {
                name <=> o.name
            }
        }
    }
}
