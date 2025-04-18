package org.grails.gorm.graphql.entity.dsl.helpers

import groovy.transform.CompileStatic

/**
 * Decorates a class with a name property and builder method
 *
 * @param <T> The class the trait is applied to
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait Named<T> {

    String name

    T name(String name)  {
        this.name = name
        (T)this
    }
}
