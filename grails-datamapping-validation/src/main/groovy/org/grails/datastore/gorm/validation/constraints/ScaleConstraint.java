package org.grails.datastore.gorm.validation.constraints;

import grails.gorm.validation.ConstrainedProperty;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

import java.math.BigDecimal;

/**
 * Manages the scale for floating point numbers (i.e., the
 * number of digits to the right of the decimal point).
 *
 * Supports properties of the following types:
 * <ul>
 * <li>java.lang.Float</li>
 * <li>java.lang.Double</li>
 * <li>java.math.BigDecimal (and its subclasses)</li>
 * </ul>
 *
 * When applied, determines if the number includes more
 * nonzero decimal places than the scale permits. If so, it rounds the number
 * to the maximum number of decimal places allowed by the scale.
 *
 * The rounding behavior described above occurs automatically when the
 * constraint is applied. This constraint does <i>not</i> generate
 * validation errors.
 *
 * @author Jason Rudolph
 * @since 0.4
 */
public class ScaleConstraint extends AbstractConstraint {

    private final int scale;

    public ScaleConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        this.scale = (int) this.constraintParameter;
    }

    /*
     * {@inheritDoc}
     * @see org.apache.groovy.grails.validation.Constraint#supports(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null && (
                BigDecimal.class.isAssignableFrom(type) ||
                        ClassUtils.isAssignableOrConvertibleFrom(Float.class, type) ||
                        ClassUtils.isAssignableOrConvertibleFrom(Double.class, type));
    }

    /*
     * {@inheritDoc}
     * @see org.apache.groovy.grails.validation.Constraint#getName()
     */
    public String getName() {
        return ConstrainedProperty.SCALE_CONSTRAINT;
    }

    /**
     * @return the scale
     */
    public int getScale() {
        return scale;
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Integer)) {
            throw new IllegalArgumentException("Parameter for constraint [" + getName() + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must be a of type [java.lang.Integer]");
        }

        int requestedScale = ((Integer) constraintParameter).intValue();

        if (requestedScale < 0) {
            throw new IllegalArgumentException("Parameter for constraint [" + getName() + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass +
                    "] must have a nonnegative value");
        }
        return constraintParameter;
    }


    /**
     * {@inheritDoc}
     * @see AbstractConstraint#processValidate(
     *     java.lang.Object, java.lang.Object, org.springframework.validation.Errors)
     */
    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        BigDecimal bigDecimal;

        BeanWrapper bean = new BeanWrapperImpl(target);

        if (propertyValue instanceof Float) {
            bigDecimal = new BigDecimal(propertyValue.toString());
            bigDecimal = getScaledValue(bigDecimal);
            bean.setPropertyValue(getPropertyName(), bigDecimal.floatValue());
        }
        else if (propertyValue instanceof Double) {
            bigDecimal = new BigDecimal(propertyValue.toString());
            bigDecimal = getScaledValue(bigDecimal);
            bean.setPropertyValue(getPropertyName(), bigDecimal.doubleValue());
        }
        else if (propertyValue instanceof BigDecimal) {
            bigDecimal = (BigDecimal) propertyValue;
            bigDecimal = getScaledValue(bigDecimal);
            bean.setPropertyValue(getPropertyName(), bigDecimal);
        }
        else {
            throw new IllegalArgumentException("Unsupported type detected in constraint [" + getName() +
                    "] of property [" + constraintPropertyName + "] of class [" + constraintOwningClass + "]");
        }
    }

    /**
     * @return the <code>BigDecimal</code> object that results from applying the contraint's scale to the underlying number
     * @param originalValue The original value
     */
    private BigDecimal getScaledValue(BigDecimal originalValue) {
        if (originalValue.scale() > scale) {
            return originalValue.setScale(scale, BigDecimal.ROUND_HALF_UP);
        }
        return originalValue;
    }
}

