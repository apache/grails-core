package org.grails.datastore.gorm.validation.constraints.registry

import grails.gorm.validation.Constraint
import org.grails.datastore.gorm.validation.constraints.factory.ConstraintFactory

/**
 * A registry of Constraint factories
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface ConstraintRegistry {

    String DEFAULT_CONSTRAINTS = 'grails.gorm.default.constraints'

    /**
     * Adds a constraint factory
     *
     * @param name The name of the constraint (example: nullable, blank etc.)
     * @param constraintFactory The constraint factory
     */
    void addConstraintFactory(ConstraintFactory constraintFactory)

    /**
     * Adds a constraint for the given class
     *
     * @param constraintClass The constraint class
     * @param targetPropertyTypes the target types if any
     */
    void addConstraint(Class<? extends Constraint> constraintClass, List<Class> targetPropertyTypes)

    /**
     * Adds a constraint for the given class
     *
     * @param constraintClass The constraint class
     */
    void addConstraint(Class<? extends Constraint> constraintClass)

    /**
     * Finds a named constraint
     *
     * @param name The short name of the constraint
     * @return The constraint
     */
    List<ConstraintFactory> findConstraintFactories(String name)

    /**
     * Finds a constraint by class
     *
     * @param name The short name of the constraint
     * @return The constraint
     */
    public <T extends Constraint> List<ConstraintFactory<T>> findConstraintFactories(Class<T> constraintType)
}