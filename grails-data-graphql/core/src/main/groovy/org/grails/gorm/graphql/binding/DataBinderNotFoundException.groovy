package org.grails.gorm.graphql.binding

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DataBinderNotFoundException extends RuntimeException {

    DataBinderNotFoundException(PersistentEntity entity) {
        this(entity.javaClass)
    }

    DataBinderNotFoundException(Class clazz) {
        super("A GraphQL data binder could not be found for ${clazz.name}")
    }
}
