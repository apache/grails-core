package org.grails.gorm.graphql.types

/**
 * @author James Kleeh
 * @since 1.0.0
 */
class TypeNotFoundException extends RuntimeException {

    TypeNotFoundException(Class clazz) {
        super("A GraphQL type could not be found for ${clazz.name}")
    }
}
