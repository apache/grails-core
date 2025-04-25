package org.apache.grails.common.compiler

import groovy.transform.CompileStatic

/**
 * Helper class to store the transformation order for groovy based AST transformations
 */
@CompileStatic
interface GroovyTransformOrder {
    /**
     * Similar to Groovy's @Delegate AST transform but instead assumes the first
     * argument to every method is 'this'.
     */
    static final int API_DELEGATE_ORDER = 5

    /**
     * Adds global imports
     */
    static final int GLOBAL_IMPORT_ORDER = API_DELEGATE_ORDER + 5

    /**
     * Adds methods from one class onto another
     */
    static final int MIXIN_ORDER = GLOBAL_IMPORT_ORDER + 5

    /**
     * Used to apply transformers to classes not located in Grails
     * directory structure, i.e. @Artefact('Controller')
     */
    static final int ARTIFACT_TYPE_ORDER = MIXIN_ORDER + 5

    /**
     * Transforms a given class to a GORM domain object
     */
    static final int ENTITY_ORDER = ARTIFACT_TYPE_ORDER + 5

    /**
     * Transform that finds any `@Enhance` annotation on traits to automatically add this trait to that artefact type
     */
    static final int ENHANCES_ORDER = ENTITY_ORDER + 5

    /**
     * Grails allows applying transforms by artefact type, this transformation is what implements that
     */
    static final int GRAILS_TRANSFORM_ORDER = ENHANCES_ORDER + 50
}