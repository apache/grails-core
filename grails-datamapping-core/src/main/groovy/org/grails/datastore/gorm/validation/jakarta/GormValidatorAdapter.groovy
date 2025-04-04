package org.grails.datastore.gorm.validation.jakarta

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidateable
import org.springframework.validation.beanvalidation.SpringValidatorAdapter

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import jakarta.validation.executable.ExecutableValidator

/**
 * A validator adapter that applies translates the constraint errors into the Errors object of a GORM entity
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class GormValidatorAdapter extends SpringValidatorAdapter {

    final Validator thisValidator

    GormValidatorAdapter(Validator targetValidator) {
        super(targetValidator)
        thisValidator = targetValidator
    }

    @Override
    def <T> Set<ConstraintViolation<T>> validate(T object, Class<?>[] groups) {
        def constraintViolations = super.validate(object, groups)
        if(object instanceof GormValidateable) {
            def errors = ((GormValidateable) object).getErrors()
            processConstraintViolations(constraintViolations, errors)
        }
        return constraintViolations
    }

    @Override
    @CompileDynamic
    ExecutableValidator forExecutables() {
        return thisValidator.forExecutables()
    }
}
