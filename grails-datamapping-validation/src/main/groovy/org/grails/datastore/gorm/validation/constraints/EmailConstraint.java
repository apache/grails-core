package org.grails.datastore.gorm.validation.constraints;


import grails.gorm.validation.ConstrainedProperty;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.context.MessageSource;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

/**
 * Validates an email address.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class EmailConstraint extends AbstractConstraint {

    private final boolean email;

    public EmailConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.email = (boolean) this.constraintParameter;
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && String.class.isAssignableFrom(type);
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Boolean)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.EMAIL_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must be a boolean value");
        }

        return constraintParameter;
    }


    public String getName() {
        return ConstrainedProperty.EMAIL_CONSTRAINT;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (!email) {
            return;
        }

        EmailValidator emailValidator = EmailValidator.getInstance();
        Object[] args = new Object[] { constraintPropertyName, constraintOwningClass, propertyValue };
        String value = propertyValue.toString();
        if (StringUtils.isEmpty(value)) {
            return;
        }

        if (!emailValidator.isValid(value)) {
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_EMAIL_MESSAGE_CODE,
                    ConstrainedProperty.EMAIL_CONSTRAINT + ConstrainedProperty.INVALID_SUFFIX, args);
        }
    }
}

