package org.grails.gorm.graphql.entity.dsl

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.entity.operations.ListOperation
import org.grails.gorm.graphql.entity.operations.ProvidedOperation

/**
 * Stores metadata about the default operations provided
 * by this library
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class Operations {

    ProvidedOperation mutation = new ProvidedOperation()
    ProvidedOperation query = new ProvidedOperation()
    ProvidedOperation all = new ProvidedOperation()
    ProvidedOperation get = new ProvidedOperation()
    ListOperation list = new ListOperation()
    ProvidedOperation create = new ProvidedOperation()
    ProvidedOperation update = new ProvidedOperation()
    ProvidedOperation delete = new ProvidedOperation()
    ProvidedOperation count = new ProvidedOperation()
}
