package org.apache.grails.common.compiler

import groovy.transform.CompileStatic

/**
 * Helper class to store the transformation order for groovy based AST transformations
 */
@CompileStatic
interface GroovyTransformOrder {

    /**
     * Gorm transforms
     */
    static final int GORM_TRANSFORMS_ORDER = 5

    /**
     * Similar to Groovy's @Delegate AST transform but instead assumes the first
     * argument to every method is 'this'.
     */
    static final int API_DELEGATE_ORDER = GORM_TRANSFORMS_ORDER + 5

    /**
     * Adds global imports
     */
    static final int GLOBAL_IMPORT_ORDER = API_DELEGATE_ORDER + 5

    /**
     * Allows specifying the format for a field when binding
     */
    static final int BINDING_FORMAT_ORDER = GLOBAL_IMPORT_ORDER + 5

    /**
     * Adds methods from one class onto another
     */
    static final int MIXIN_ORDER = BINDING_FORMAT_ORDER + 5

    /**
     * Used to apply transformers to classes not located in Grails
     * directory structure, i.e. @Artefact('Controller')
     */
    static final int ARTIFACT_TYPE_ORDER = MIXIN_ORDER + 5

    /**
     * Adds basic fields like id, version, toString, and associations
     * Adds the DomainClassArtefactType
     */
    static final int ENTITY_ORDER = ARTIFACT_TYPE_ORDER + 5

    /**
     * Transforms a given class to a GORM Entity
     */
    static final int GORM_ENTITY_ORDER = ARTIFACT_TYPE_ORDER + 5

    /**
     * Enables dirty tracking to occur at the domain class level instead of at the ORM level
     */
    static final int DIRTY_CHECK_ORDER = GORM_ENTITY_ORDER + 5

    /**
     * Transforms a JPA entity into a GORM entity
     */
    static final int JPA_ORDER = DIRTY_CHECK_ORDER + 5

    /**
     * Makes all GormEntities be a JPA entity
     */
    static final int JPA_GORM_ENTITY_ORDER = JPA_ORDER + 5

    /**
     * getter/setter transforms for hibernate entities
     */
    static final int HIBERNATE5_ORDER = JPA_GORM_ENTITY_ORDER + 5

    /**
     * Transform that finds any `@Enhance` annotation on traits to automatically add this trait to that artefact type
     */
    static final int ENHANCES_ORDER = HIBERNATE5_ORDER + 5

    /**
     * Allows adding link() support to any class
     */
    static final int LINK_ORDER = ENHANCES_ORDER + 5

    /**
     * Exposes a domain class as a restful resource
     */
    static final int RESOURCE_ORDER = LINK_ORDER + 5

    /**
     * Enhances view scripts with Trait behavior
     */
    static final int VIEWS_ORDER = RESOURCE_ORDER + 5

    /**
     * Adds line numbers to GSPs
     */
    static final int GSP_LINE_ORDER = VIEWS_ORDER + 5

    /**
     * Changes methods in a file to return a promise instead of a value
     */
    static final int DELEGATE_ASYNC_ORDER = GSP_LINE_ORDER + 5

    /**
     * Changes methods in a file to return a promise instead of a value
     */
    static final int GORM_ASYNC_ORDER = DELEGATE_ASYNC_ORDER + 5

    /**
     * Grails allows applying transforms by artefact type, this transformation is what implements that
     */
    static final int GRAILS_TRANSFORM_ORDER = GORM_ASYNC_ORDER + 50

    /**
     * Transforms groovy finders into DetachedCriteria
     */
    static final int FINDER_ORDER = GRAILS_TRANSFORM_ORDER + 5

    /**
     * Transforms where queries into DetachedCriteria
     */
    static final int WHERE_ORDER = FINDER_ORDER + 5
}