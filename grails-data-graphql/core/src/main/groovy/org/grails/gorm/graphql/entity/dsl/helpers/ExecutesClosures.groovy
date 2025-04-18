package org.grails.gorm.graphql.entity.dsl.helpers

import groovy.transform.CompileStatic

/**
 * Decorates a class with the ability to execute closures with
 * a delegate
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait ExecutesClosures {

    static void withDelegate(@DelegatesTo(strategy = Closure.DELEGATE_ONLY)Closure closure, Object delegate) {
        if (closure != null) {
            closure.resolveStrategy = Closure.DELEGATE_ONLY
            closure.delegate = delegate

            try {
                closure.call()
            } finally {
                closure.delegate = null
            }
        }
    }
}

