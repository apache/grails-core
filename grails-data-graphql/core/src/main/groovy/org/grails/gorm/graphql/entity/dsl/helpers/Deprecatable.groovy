package org.grails.gorm.graphql.entity.dsl.helpers

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.Schema

/**
 * Decorates a class with a builder syntax to provide
 * deprecation data.
 *
 * @param <T> The implementing class
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait Deprecatable<T> {

    boolean deprecated = false
    String deprecationReason

    T deprecated(boolean deprecated) {
        this.deprecated = deprecated
        (T)this
    }

    T deprecationReason(String deprecationReason) {
        this.deprecationReason = deprecationReason
        (T)this
    }

    String getDeprecationReason() {
        deprecationReason ?: (deprecated ? Schema.DEFAULT_DEPRECATION_REASON : null)
    }

}
