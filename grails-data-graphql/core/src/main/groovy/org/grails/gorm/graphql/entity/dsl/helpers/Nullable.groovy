package org.grails.gorm.graphql.entity.dsl.helpers

import groovy.transform.CompileStatic

/**
 * Decorates a class with a nullable property and builder method
 *
 * @param <T> The class the trait is applied to
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait Nullable<T> {

    boolean nullable = true

    T nullable(boolean nullable) {
        this.nullable = nullable
        (T)this
    }
}
