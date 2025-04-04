package org.grails.datastore.gorm.validation.constraints.factory

import grails.gorm.validation.Constraint

/**
 * Constructs a constraint
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface ConstraintFactory<T extends Constraint> {

    /**
     * @return The type of the constraint
     */
    Class<T> getType()

    /**
     *
     * @return The name of the constraint
     */
    String getName()

    /**
     * The target type this factory supports
     *
     * @param targetType The target type
     * @return True if it does support the given target type
     */
    boolean supports(Class targetType)

    /**
     * Builds a constraint
     *
     * @param owner The owner
     * @param property The property
     * @param constrainingValue The constrainting value
     * @return A constraint instance
     */
    T build(Class owner, String property, Object constrainingValue)
}