package org.grails.datastore.gorm.validation.constraints;

import grails.gorm.validation.VetoingConstraint;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * A constraint that can veto further constraint processing
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractVetoingConstraint extends AbstractConstraint implements VetoingConstraint {

    public AbstractVetoingConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
    }

    public boolean validateWithVetoing(Object target, Object propertyValue, Errors errors) {
        checkState();
        if (propertyValue == null && skipNullValues()) {
            return false;
        }

        return processValidateWithVetoing(target, propertyValue, errors);
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        processValidateWithVetoing(target, propertyValue, errors);
    }

    protected abstract boolean processValidateWithVetoing(Object target, Object propertyValue, Errors errors);
}
