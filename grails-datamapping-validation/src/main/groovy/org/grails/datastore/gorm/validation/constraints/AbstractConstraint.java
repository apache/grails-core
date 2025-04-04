package org.grails.datastore.gorm.validation.constraints;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import grails.gorm.validation.ConstrainedProperty;
import grails.gorm.validation.Constraint;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

/**
 * Abstract class for constraints to extend.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractConstraint implements Constraint {

    protected final Class<?> constraintOwningClass;
    protected final String constraintPropertyName;
    protected final Object constraintParameter;
    protected final String classShortName;
    protected final MessageSource messageSource;
    private final String fullQualifiedConstraintErrorCode;
    private final String shortNameConstraintErrorCode;
    private final String fullQualifiedPrefix;
    private final String shortNamePrefix;

    public AbstractConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        this.constraintPropertyName = constraintPropertyName;
        this.constraintOwningClass = constraintOwningClass;
        this.constraintParameter = validateParameter(constraintParameter);
        this.messageSource = messageSource;
        classShortName = Introspector.decapitalize(constraintOwningClass.getSimpleName());
        fullQualifiedPrefix = constraintOwningClass.getName() + '.' + constraintPropertyName + '.';
        shortNamePrefix = classShortName + '.' + constraintPropertyName + '.';
        fullQualifiedConstraintErrorCode = fullQualifiedPrefix + getName() + ".error";
        shortNameConstraintErrorCode = shortNamePrefix + getName() + ".error";
    }

    /**
     * Validate the parameter passed
     *
     * @param constraintParameter The parameter to validate
     *
     * @return The validated parameter
     */
    protected abstract Object validateParameter(Object constraintParameter);

    public String getPropertyName() {
        return constraintPropertyName;
    }

    public Object getParameter() {
        return constraintParameter;
    }

    protected void checkState() {
        Assert.hasLength(constraintPropertyName, "Property 'propertyName' must be set on the constraint");
        Assert.notNull(constraintOwningClass, "Property 'owningClass' must be set on the constraint");
        Assert.notNull(constraintParameter, "Property 'constraintParameter' must be set on the constraint");
    }

    public void validate(Object target, Object propertyValue, Errors errors) {
        checkState();

        // Skip null values if desired.
        if (propertyValue == null && skipNullValues()) {
            return;
        }

        // Skip blank values if desired.
        if (skipBlankValues() && propertyValue instanceof String && StringUtils.isEmpty(propertyValue)) {
            return;
        }

        // Do the validation for this constraint.
        processValidate(target, propertyValue, errors);
    }

    protected boolean skipNullValues() {
        // a null is not a value we should even check in most cases
        return true;
    }

    protected boolean skipBlankValues() {
        // Most constraints ignore blank values, leaving it to the explicit "blank" constraint.
        return true;
    }

    public void rejectValue(Object target, Errors errors, String defaultMessageCode, Object[] args) {
        rejectValue(target, errors, defaultMessageCode, new String[] {}, args);
    }

    public void rejectValue(Object target,Errors errors, String defaultMessageCode, String code, Object[] args) {
        rejectValue(target,errors, defaultMessageCode, new String[] {code}, args);
    }

    public void rejectValue(Object target,Errors errors, String defaultMessageCode, String[] codes, Object[] args) {
        rejectValueWithDefaultMessage(target, errors, getDefaultMessage(defaultMessageCode), codes, args);
    }

    public void rejectValueWithDefaultMessage(Object target, Errors errors, String defaultMessage, String[] codes, Object[] args) {
        BindingResult result = (BindingResult) errors;
        Set<String> newCodes = new LinkedHashSet<String>();

        if (args.length > 1 && messageSource != null) {
            if ((args[0] instanceof String) && (args[1] instanceof Class<?>)) {
                final Locale locale = LocaleContextHolder.getLocale();
                final Class<?> constrainedClass = (Class<?>) args[1];
                final String fullClassName = constrainedClass.getName();

                String classNameCode = fullClassName + ".label";
                String resolvedClassName = messageSource.getMessage(classNameCode, null, fullClassName, locale);
                final String classAsPropertyName = Introspector.decapitalize(constrainedClass.getSimpleName());

                if (resolvedClassName.equals(fullClassName)) {
                    // try short version
                    classNameCode = classAsPropertyName+".label";
                    resolvedClassName = messageSource.getMessage(classNameCode, null, fullClassName, locale);
                }

                // update passed version
                if (!resolvedClassName.equals(fullClassName)) {
                    args[1] = resolvedClassName;
                }

                String propertyName = (String)args[0];
                String propertyNameCode = fullClassName + '.' + propertyName + ".label";
                String resolvedPropertyName = messageSource.getMessage(propertyNameCode, null, propertyName, locale);
                if (resolvedPropertyName.equals(propertyName)) {
                    propertyNameCode = classAsPropertyName + '.' + propertyName + ".label";
                    resolvedPropertyName = messageSource.getMessage(propertyNameCode, null, propertyName, locale);
                }

                // update passed version
                if (!resolvedPropertyName.equals(propertyName)) {
                    args[0] = resolvedPropertyName;
                }
            }
        }

        //Qualified class name is added first to match before unqualified class (which is still resolved for backwards compatibility)
        newCodes.addAll(Arrays.asList(result.resolveMessageCodes(fullQualifiedConstraintErrorCode, constraintPropertyName)));
        newCodes.addAll(Arrays.asList(result.resolveMessageCodes(shortNameConstraintErrorCode, constraintPropertyName)));
        for (String code : codes) {
            newCodes.addAll(Arrays.asList(result.resolveMessageCodes(fullQualifiedPrefix + code, constraintPropertyName)));
            newCodes.addAll(Arrays.asList(result.resolveMessageCodes(shortNamePrefix + code, constraintPropertyName)));
            //We resolve the error code on it's own last so that a global code doesn't override a class/field specific error
            newCodes.addAll(Arrays.asList(result.resolveMessageCodes(code, constraintPropertyName)));
        }

        FieldError error = new FieldError(
                errors.getObjectName(),
                errors.getNestedPath() + constraintPropertyName,
                getPropertyValue(errors, target),
                false,
                newCodes.toArray(new String[newCodes.size()]),
                args,
                defaultMessage);
        ((BindingResult)errors).addError(error);
    }

    private Object getPropertyValue(Errors errors, Object target) {
        try {
            return errors.getFieldValue(constraintPropertyName);
        }
        catch (Exception nre) {
            int i = constraintPropertyName.lastIndexOf(".");
            String propertyName;
            if (i > -1) {
                propertyName = constraintPropertyName.substring(i, constraintPropertyName.length());
            }
            else {
                propertyName = constraintPropertyName;
            }
            return new BeanWrapperImpl(target).getPropertyValue(propertyName);
        }
    }

    // For backward compatibility
    public void rejectValue(Object target, Errors errors, String code, String defaultMessage) {
        rejectValueWithDefaultMessage(target, errors, defaultMessage, new String[] {code}, null);
    }

    // For backward compatibility
    public void rejectValue(Object target, Errors errors, String code, Object[] args, String defaultMessage) {
        rejectValueWithDefaultMessage(target, errors, defaultMessage, new String[] {code}, args);
    }

    /**
     * Returns the default message for the given message code in the
     * current locale. Note that the string returned includes any
     * placeholders that the required message has - these must be
     * expanded by the caller if required.
     * @param code The i18n message code to look up.
     * @return The message corresponding to the given code in the
     * current locale.
     */
    protected String getDefaultMessage(String code) {
        try {
            if (messageSource != null) {
                return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
            }

            return ConstrainedProperty.DEFAULT_MESSAGES.get(code);
        }
        catch (Exception e) {
            return ConstrainedProperty.DEFAULT_MESSAGES.get(code);
        }
    }

    protected abstract void processValidate(Object target, Object propertyValue, Errors errors);

    @Override
    public String toString() {
        return new ToStringCreator(this).append(constraintParameter).toString();
    }

    /**
     * Return whether the constraint is valid for the owning class
     *
     * @return true if it is
     */
    public boolean isValid() {
        return true;
    }
}
