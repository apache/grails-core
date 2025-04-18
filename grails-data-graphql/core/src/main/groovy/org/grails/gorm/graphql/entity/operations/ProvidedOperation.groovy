package org.grails.gorm.graphql.entity.operations

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.entity.dsl.helpers.Deprecatable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable

/**
 * Stores metadata about the operations that this library
 * provides by default. Also allows the user to disable the
 * operation.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class ProvidedOperation implements Describable<ProvidedOperation>, Deprecatable<ProvidedOperation> {

    boolean enabled = true

    ProvidedOperation enabled(boolean enabled) {
        this.enabled = enabled
        this
    }
}
