package org.grails.datastore.gorm.validation.constraints;

import grails.gorm.validation.ConstrainedProperty;
import org.apache.commons.validator.routines.CreditCardValidator;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * Validates a credit card number.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class CreditCardConstraint extends AbstractConstraint {

    private final boolean creditCard;

    public CreditCardConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        creditCard = (boolean) this.constraintParameter;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (!creditCard) {
            return;
        }

        CreditCardValidator validator = new CreditCardValidator();

        if (!validator.isValid(propertyValue.toString())) {
            Object[] args = new Object[] { constraintPropertyName, constraintOwningClass, propertyValue };
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_CREDIT_CARD_MESSAGE_CODE,
                    ConstrainedProperty.CREDIT_CARD_CONSTRAINT + ConstrainedProperty.INVALID_SUFFIX, args);
        }
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Boolean)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.CREDIT_CARD_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] must be a boolean value");
        }
        return constraintParameter;
    }


    public String getName() {
        return ConstrainedProperty.CREDIT_CARD_CONSTRAINT;
    }

    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && String.class.isAssignableFrom(type);
    }
}
