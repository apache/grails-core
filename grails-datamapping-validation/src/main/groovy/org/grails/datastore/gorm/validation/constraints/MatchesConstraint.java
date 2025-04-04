package org.grails.datastore.gorm.validation.constraints;


import grails.gorm.validation.ConstrainedProperty;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * Validates the property against a supplied regular expression.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class MatchesConstraint extends AbstractConstraint {

    private final String regex;

    public MatchesConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.regex = this.constraintParameter.toString();
    }

    /**
     * @return Returns the regex.
     */
    public String getRegex() {
        return regex;
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
        if (!(constraintParameter instanceof CharSequence)) {
            throw new IllegalArgumentException("Parameter for constraint [" + ConstrainedProperty.MATCHES_CONSTRAINT +
                    "] of property [" + constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] must be of type [CharSequence]");
        }
        return constraintParameter;
    }

    public String getName() {
        return ConstrainedProperty.MATCHES_CONSTRAINT;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (propertyValue.toString().matches(regex)) {
            return;
        }

        rejectValue(target, errors, ConstrainedProperty.DEFAULT_DOESNT_MATCH_MESSAGE_CODE,
                ConstrainedProperty.MATCHES_CONSTRAINT + ConstrainedProperty.INVALID_SUFFIX,
                new Object[] { constraintPropertyName, constraintOwningClass, propertyValue, regex });
    }
}

