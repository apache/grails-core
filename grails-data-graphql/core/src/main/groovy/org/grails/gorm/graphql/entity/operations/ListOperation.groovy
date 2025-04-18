package org.grails.gorm.graphql.entity.operations

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.gorm.graphql.entity.dsl.helpers.Deprecatable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable

/**
 * Stores metadata about the list operation that this library
 * provides by default. Also allows the user to disable the
 * operation and convert the object type to support pagination.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@Builder(prefix = '', builderStrategy = SimpleStrategy)
@CompileStatic
class ListOperation implements Describable<ListOperation>, Deprecatable<ListOperation> {
    boolean enabled = true
    boolean paginate = false
}
