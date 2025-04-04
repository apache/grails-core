package org.grails.datastore.gorm.validation.constraints;

import grails.gorm.validation.ConstrainedProperty;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * Validates not null.
 *
 * @author Graeme Rocher
 * @author Sergey Nebolsin
 * @since 0.4
 */
public class NullableConstraint extends AbstractVetoingConstraint {

    private final boolean nullable;

    public NullableConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.nullable = (boolean) this.constraintParameter;
    }

    public boolean isNullable() {
        return nullable;
    }

    /* (non-Javadoc)
     * @see org.apache.groovy.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && !type.isPrimitive();
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Boolean)) {
            throw new IllegalArgumentException("Parameter for constraint [" + ConstrainedProperty.NULLABLE_CONSTRAINT +
                    "] of property [" + constraintPropertyName + "] of class [" +
                    constraintOwningClass + "] must be a boolean value");
        }

        return constraintParameter;
    }

    public String getName() {
        return ConstrainedProperty.NULLABLE_CONSTRAINT;
    }

    @Override
    protected boolean skipNullValues() {
        return false;
    }

    @Override
    protected boolean processValidateWithVetoing(Object target, Object propertyValue, Errors errors) {
        if (propertyValue == null) {
            if (!nullable) {
                Object[] args = new Object[] { constraintPropertyName, constraintOwningClass };
                rejectValue(target, errors, ConstrainedProperty.DEFAULT_NULL_MESSAGE_CODE,
                        ConstrainedProperty.NULLABLE_CONSTRAINT, args);
                // null value is caught by 'blank' constraint, no addition validation needed
                return true;
            }
        }
        return false;
    }
}
