package org.grails.datastore.gorm.validation.jakarta

import grails.gorm.services.Service
import groovy.transform.CompileStatic
import org.springframework.validation.Errors
import org.springframework.validation.MapBindingResult

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException

/**
 * Utility methods for handling ConstraintViolationException
 *
 * @author Graeme Rocher
 * @since 6.1.3
 */
@CompileStatic
class ConstraintViolationUtils {

    /**
     * Converts a ConstraintViolationException to errors
     *
     * @param object The validated object
     * @param e The exception
     * @return The errors
     */
    static Errors asErrors(Object object, ConstraintViolationException e) {
        Set<ConstraintViolation<?>> constraintViolations = e.constraintViolations
        return asErrors(object, constraintViolations)
    }

    /**
     * Converts a ConstraintViolation instances to errors
     *
     * @param object The validated object
     * @param e The exception
     * @return The errors
     */
    static Errors asErrors(Object object, Set<ConstraintViolation> constraintViolations) {
        Service ann = object.getClass().getAnnotation(Service)
        String objectName = ann != null ? ann.name() : object.getClass().simpleName
        Map errorMap = [:]
        Errors errors = new MapBindingResult(errorMap, objectName)
        for (violation in constraintViolations) {
            String property = violation.propertyPath.last().name
            errorMap.put(property, violation.invalidValue)
            String code = "${objectName}.${violation.propertyPath}"
            String message = "${property} $violation.message"
            errors.rejectValue(property, code, [violation.invalidValue] as Object[], message)
        }
        return errors
    }
}
