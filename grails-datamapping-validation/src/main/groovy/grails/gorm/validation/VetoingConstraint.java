package grails.gorm.validation;

import org.springframework.validation.Errors;

/**
 * <p>Marker interface for vetoing constraint.</p>
 *
 * <p>
 * Vetoing constraints are those which might return 'true' from validateWithVetoing method to prevent any additional
 * validation of the property. These constraints are proceeded before any other constraints, and validation continues
 * only if no one of vetoing constraint hadn't vetoed.
 * </p>
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public interface VetoingConstraint extends Constraint {

    /**
     * Invoke validation with vetoing capabilities
     *
     * @param target The target to validate
     * @param propertyValue The property value
     * @param errors The errors object
     * @return True if it valides
     */
    boolean validateWithVetoing(Object target, Object propertyValue, Errors errors);
}

