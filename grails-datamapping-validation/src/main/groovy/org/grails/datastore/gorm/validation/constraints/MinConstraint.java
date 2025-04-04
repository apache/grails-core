package org.grails.datastore.gorm.validation.constraints;

import grails.gorm.validation.ConstrainedProperty;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * Implements a minimum value constraint.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class MinConstraint extends AbstractConstraint {

    private final Comparable minValue;

    public MinConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.minValue = (Comparable) this.constraintParameter;
    }

    /**
     * @return Returns the minValue.
     */
    public Comparable getMinValue() {
        return minValue;
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    public boolean supports(Class type) {
        return type != null && (
                Comparable.class.isAssignableFrom(type) ||
                        ClassUtils.isAssignableOrConvertibleFrom(Number.class, type));
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (constraintParameter == null) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.MIN_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass + "] cannot be null");
        }

        if (!(constraintParameter instanceof Comparable<?>) && (!constraintParameter.getClass().isPrimitive())) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.MIN_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must implement the interface [java.lang.Comparable]");
        }

        Class<?> propertyClass = ClassPropertyFetcher.getPropertyType(constraintOwningClass, constraintPropertyName);
        if (!ClassUtils.isAssignableOrConvertibleFrom(constraintParameter.getClass(),propertyClass)) {
            throw new IllegalArgumentException("Parameter for constraint [" + ConstrainedProperty.MIN_CONSTRAINT +
                    "] of property [" + constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must be the same type as property: [" + propertyClass.getName() + "]");
        }
        return constraintParameter;
    }


    public String getName() {
        return ConstrainedProperty.MIN_CONSTRAINT;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (minValue.compareTo(propertyValue) <= 0) {
            return;
        }

        Object[] args = new Object[] { constraintPropertyName, constraintOwningClass, propertyValue, minValue };
        rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_MIN_MESSAGE_CODE,
                ConstrainedProperty.MIN_CONSTRAINT + ConstrainedProperty.NOTMET_SUFFIX, args);
    }
}

