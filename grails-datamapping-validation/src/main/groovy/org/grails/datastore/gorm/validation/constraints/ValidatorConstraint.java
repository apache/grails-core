package org.grails.datastore.gorm.validation.constraints;

import grails.gorm.validation.ConstrainedProperty;
import groovy.lang.Closure;

import java.util.Collection;

import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;

/**
 * <p>A constraint class that validates using a user-supplied closure.</p>
 * <p>The Closure will receive one or two parameters containing the new value of the property and the object
 * on which the validation is being performed. The value is always the first parameterm and the object is the second.
 * These parameters must be type compatible with the value of the property and constrained class.</p>
 *
 * <p>
 * The Closure can return any of:
 * </p>
 * <ul>
 * <li>NULL to indicate success
 * <li>true to indicate success
 * <li>false to indicate a failure, with the default failure message
 * <li>a string to indicate a failure with the specific error code which will be appended to the
 * prefix for the constrained class and property i.e. classname.propertyname.stringfromclosurehere
 * <li>a list containing an error code and any other arguments for the error message. The error code will
 * be appended to the standard classname.propertyname prefix and the arguments made available to the
 * error message as parameters numbered 3 onwards.
 * </ul>
 *
 * @author Marc Palmer
 * @author Graeme Rocher
 * @since 6.0
 */
public class ValidatorConstraint extends AbstractConstraint {

    private final Closure<?> validator;
    private final int numValidatorParams;

    public ValidatorConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource);
        validator = (Closure<?>) this.constraintParameter;
        Class<?>[] params = validator.getParameterTypes();
        numValidatorParams = params.length;
    }

    @Override
    protected Object validateParameter(Object constraintParameter) {
        if (!(constraintParameter instanceof Closure)) {
            throw new IllegalArgumentException("Parameter for constraint [" +
                    ConstrainedProperty.VALIDATOR_CONSTRAINT + "] of property [" +
                    constraintPropertyName + "] of class [" + constraintOwningClass + "] must be a Closure");
        }
        Closure validator = (Closure) constraintParameter;
        Class<?>[] params = validator.getParameterTypes();
        // Groovy should always force one parameter, but let's check anyway...
        if (params.length == 0) {
            throw new IllegalArgumentException("Parameter for constraint ["+ConstrainedProperty.VALIDATOR_CONSTRAINT+"] of property ["+constraintPropertyName+"] of class ["+constraintOwningClass+"] must be a Closure taking at least 1 parameter (value, [object])");
        }

        if (params.length > 3) {
            throw new IllegalArgumentException("Parameter for constraint ["+ConstrainedProperty.VALIDATOR_CONSTRAINT+"] of property ["+constraintPropertyName+"] of class ["+constraintOwningClass+"] must be a Closure taking no more than 3 parameters (value, [object, [errors]])");
        }
        return constraintParameter;
    }

    @Override
    protected boolean skipNullValues() {
        return false;
    }

    @Override
    protected boolean skipBlankValues() {
        return false;
    }

    @Override
    protected void processValidate(Object target, Object propertyValue, Errors errors) {
        if (validator == null) {
            return;
        }

        Object[] params = new Object[numValidatorParams];
        params[0] = propertyValue;
        if (numValidatorParams >= 2) {
            params[1] = target;
        }
        if (numValidatorParams == 3) {
            params[2] = errors;
        }

        // Provide some extra information via a closure delegate.
        // The custom validator can access the properties of this
        // delegate as if they were already defined as local variables.
        final ValidatorDelegate delegate = new ValidatorDelegate();
        delegate.setPropertyName(getPropertyName());
        validator.setDelegate(delegate);

        // Execute the custom validation.
        final Object result = validator.call(params);

        if (numValidatorParams == 3) {
            // If the closure has been passed the errors
            // object no further action has to be taken.
            return;
        }

        boolean bad = false;
        String errmsg = null;
        Object[] args = null;

        if (result != null) {
            if (result instanceof Boolean) {
                bad = !(Boolean)result;
            }
            else if (result instanceof CharSequence) {
                bad = true;
                errmsg = result.toString();
            }
            else if ((result instanceof Collection<?>) || result.getClass().isArray()) {
                bad = true;
                Object[] values = (result instanceof Collection<?>) ? ((Collection<?>)result).toArray() : (Object[])result;
                if (!(values[0] instanceof String)) {
                    throw new IllegalArgumentException("Return value from validation closure [" +
                            ConstrainedProperty.VALIDATOR_CONSTRAINT+"] of property ["+constraintPropertyName+"] of class [" +
                            constraintOwningClass+"] is returning a list but the first element must be a string " +
                            "containing the error message code");
                }
                errmsg = (String)values[0];
                args = new Object[values.length - 1 + 3];
                int i = 0;
                args[i++] = constraintPropertyName;
                args[i++] = constraintOwningClass;
                args[i++] = propertyValue;
                System.arraycopy(values, 1, args, i, values.length - 1);
            }
            else {
                throw new IllegalArgumentException("Return value from validation closure [" +
                        ConstrainedProperty.VALIDATOR_CONSTRAINT+"] of property [" + constraintPropertyName +
                        "] of class [" + constraintOwningClass +
                        "] must be a boolean, a string, an array or a collection");
            }
        }
        if (bad) {
            if (args == null) {
                args = new Object[] { constraintPropertyName, constraintOwningClass, propertyValue };
            }
            rejectValue(target, errors, ConstrainedProperty.DEFAULT_INVALID_VALIDATOR_MESSAGE_CODE,
                    errmsg == null ? ConstrainedProperty.VALIDATOR_CONSTRAINT + ConstrainedProperty.INVALID_SUFFIX: errmsg, args);
        }
    }

    public String getName() {
        return ConstrainedProperty.VALIDATOR_CONSTRAINT;
    }

    @SuppressWarnings("rawtypes")
    public boolean supports(Class type) {
        return type != null;
    }

    private static class ValidatorDelegate {
        private String propertyName;

        @SuppressWarnings("unused")
        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }
    }
}

