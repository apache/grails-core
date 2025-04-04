package org.grails.datastore.gorm.validation.constraints.eval

import grails.gorm.validation.ConstrainedProperty
import org.grails.datastore.gorm.validation.constraints.builder.ConstrainedPropertyBuilder
import org.grails.datastore.mapping.model.config.GormProperties

/**
 * Evaluates Constraints for a GORM entity
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface ConstraintsEvaluator {
    /**
     * The name of the constraints property
     */
    String PROPERTY_NAME = GormProperties.CONSTRAINTS
    /**
     * The default constraints to use
     * @return A map of default constraints
     */
    Map<String, Object> getDefaultConstraints()

    /**
     * Evaluate constraints for the given class
     *
     * @param cls The class to evaluate constraints for
     * @return A map of constrained properties
     */
    Map<String, ConstrainedProperty> evaluate(@SuppressWarnings("rawtypes") Class cls)

    /**
     * Evaluate constraints for the given class
     *
     * @param cls The class to evaluate constraints for
     * @oaram defaultNullable Whether to default to allow nullable
     * @return A map of constrained properties
     */
    Map<String, ConstrainedProperty> evaluate(@SuppressWarnings("rawtypes") Class cls, boolean defaultNullable)

    /**
     * Evaluate constraints for the given class
     *
     * @param cls The class to evaluate constraints for
     * @param defaultNullable indicates if properties are nullable by default
     * @param useOnlyAdHocConstraints indicates if evaluating without pre-declared constraints
     * @param adHocConstraintsClosures ad-hoc constraints to evaluate for
     * @return A map of constrained properties
     */
    Map<String, ConstrainedProperty> evaluate(Class<?> cls, boolean defaultNullable, boolean useOnlyAdHocConstraints, Closure... adHocConstraintsClosures)

    /**
     * Obtains a new builder from this evaluator
     *
     * @param theClass The class
     * @return The builder
     */
    ConstrainedPropertyBuilder newConstrainedPropertyBuilder(Class<?> theClass)
}