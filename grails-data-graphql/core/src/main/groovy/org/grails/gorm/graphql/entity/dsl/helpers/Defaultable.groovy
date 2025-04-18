package org.grails.gorm.graphql.entity.dsl.helpers

import groovy.transform.CompileStatic

/**
 * Decorates a class with the ability to store a default value
 *
 * @param <T> The implementing class
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait Defaultable<T> {

    Object defaultValue

    T defaultValue(Object defaultValue) {
        this.defaultValue = defaultValue
        (T)this
    }
}
