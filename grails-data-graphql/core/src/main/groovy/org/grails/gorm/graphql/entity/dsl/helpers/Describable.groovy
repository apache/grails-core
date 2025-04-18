package org.grails.gorm.graphql.entity.dsl.helpers

import groovy.transform.CompileStatic

/**
 * Decorates a class with a description property and builder method.
 *
 * @param <T> The implementing class
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait Describable<T> {

    String description

    T description(String description) {
        this.description = description
        (T)this
    }
}
