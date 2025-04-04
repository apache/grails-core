package org.grails.datastore.gorm.validation.constraints;

import grails.gorm.validation.ConstrainedProperty;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import java.lang.reflect.Array;
import java.util.Collection;

/**
 * Validates maximum size or length of the property, for strings and arrays
 * this is the length and collections the size.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public class MaxSizeConstraint extends AbstractConstraint {

    private final int maxSize;

    public MaxSizeConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.maxSize = ((Number)this.constraintParameter).intValue() ;
    }

    /**
     * @return Returns the maxSize.
     */
    public int getMaxSize() {
        return maxSize;
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Number)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.MAX_SIZE_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must be a of type [java.lang.Number]");
        }

        return constraintParameter;
    }

    public String getName() {
        return ConstrainedProperty.MAX_SIZE_CONSTRAINT;
    }

    /* (non-Javadoc)
     * @see org.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && (
                String.class.isAssignableFrom(type) ||
                        Collection.class.isAssignableFrom(type) ||
                        type.isArray());
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        int length;
        if (propertyValue.getClass().isArray()) {
            length = Array.getLength(propertyValue);
        }
        else if (propertyValue instanceof Collection<?>) {
            length = ((Collection<?>)propertyValue).size();
        }
        else { // String
            length = ((String)propertyValue).length();
        }

        if (length > maxSize) {
            Object[] args = { constraintPropertyName, constraintOwningClass, propertyValue, maxSize};
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_MAX_SIZE_MESSAGE_CODE,
                    ConstrainedProperty.MAX_SIZE_CONSTRAINT + ConstrainedProperty.EXCEEDED_SUFFIX, args);
        }
    }
}
